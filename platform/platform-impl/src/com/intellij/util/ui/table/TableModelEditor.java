/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.ui.table;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.ui.*;
import com.intellij.ui.table.TableView;
import com.intellij.util.Function;
import com.intellij.util.FunctionUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ElementProducer;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import gnu.trove.THashMap;
import gnu.trove.TObjectObjectProcedure;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

public class TableModelEditor<T> implements ElementProducer<T> {
  private final TableView<T> table;
  private final ToolbarDecorator toolbarDecorator;

  private final ItemEditor<T> itemEditor;

  private final MyListTableModel<T> model;

  /**
   * source will be copied, passed list will not be used directly
   *
   * Implement {@link DialogItemEditor} instead of {@link ItemEditor} if you want provide dialog to edit.
   */
  public TableModelEditor(@NotNull List<T> items, @NotNull ColumnInfo[] columns, @NotNull ItemEditor<T> itemEditor, @NotNull String emptyText) {
    this.itemEditor = itemEditor;

    model = new MyListTableModel<T>(columns, new ArrayList<T>(items), this);
    table = new TableView<T>(model);
    table.setStriped(true);
    new TableSpeedSearch(table);
    if (columns[0].getColumnClass() == Boolean.class && columns[0].getName().isEmpty()) {
      TableUtil.setupCheckboxColumn(table.getColumnModel().getColumn(0));
    }

    table.getEmptyText().setText(emptyText);
    toolbarDecorator = ToolbarDecorator.createDecorator(table, this);

    if (itemEditor instanceof DialogItemEditor) {
      toolbarDecorator.setEditAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          T item = table.getSelectedObject();
          if (item != null) {
            Function<T, T> mutator;
            if (model.isMutable(item)) {
              mutator = FunctionUtil.id();
            }
            else {
              final int selectedRow = table.getSelectedRow();
              mutator = new Function<T, T>() {
                @Override
                public T fun(T item) {
                  return model.getMutable(selectedRow, item);
                }
              };
            }
            ((DialogItemEditor<T>)TableModelEditor.this.itemEditor).edit(item, mutator);
          }
        }
      });
    }
  }

  public static abstract class ItemEditor<T> {
    /**
     * Used for "add" and "in place edit" actions.
     *
     * You must perform deep clone in case of "add" operation, but in case of "in place edit" you should copy only exposed (via column) properties.
     */
    public abstract T clone(@NotNull T item, boolean forInPlaceEditing);

    @NotNull
    /**
     * Class must have empty constructor.
     */
    public abstract Class<T> getItemClass();
  }

  public static abstract class DialogItemEditor<T> extends ItemEditor<T> {
    public abstract void edit(@NotNull T item, @NotNull Function<T, T> mutator);

    public abstract void applyEdited(@NotNull T oldItem, @NotNull T newItem);
  }

  @NotNull
  public static <T> T cloneUsingXmlSerialization(@NotNull T oldItem, @NotNull T newItem) {
    Element serialized = XmlSerializer.serialize(oldItem, new SkipDefaultValuesSerializationFilters());
    if (!JDOMUtil.isEmpty(serialized)) {
      XmlSerializer.deserializeInto(newItem, serialized);
    }
    return newItem;
  }

  private static final class MyListTableModel<T> extends ListTableModel<T> {
    private List<T> items;
    private final TableModelEditor<T> editor;
    private final THashMap<T, T> modifiedToOriginal = new THashMap<T, T>();

    public MyListTableModel(@NotNull ColumnInfo[] columns, @NotNull List<T> items, @NotNull TableModelEditor<T> editor) {
      super(columns, items);

      this.items = items;
      this.editor = editor;
    }

    @Override
    public void setItems(@NotNull List<T> items) {
      modifiedToOriginal.clear();
      this.items = items;
      super.setItems(items);
    }

    @Override
    public void removeRow(int index) {
      modifiedToOriginal.remove(getItem(index));
      super.removeRow(index);
    }

    @Override
    public void setValueAt(Object newValue, int rowIndex, int columnIndex) {
      if (rowIndex < getRowCount()) {
        @SuppressWarnings("unchecked")
        ColumnInfo<T, Object> column = (ColumnInfo<T, Object>)getColumnInfos()[columnIndex];
        T item = getItem(rowIndex);
        Object oldValue = column.valueOf(item);
        if (column.getColumnClass() == String.class
            ? !Comparing.strEqual(((String)oldValue), ((String)newValue))
            : !Comparing.equal(oldValue, newValue)) {

          column.setValue(getMutable(rowIndex, item), newValue);
        }
      }
    }

    private T getMutable(int rowIndex, T item) {
      if (isMutable(item)) {
        return item;
      }
      else {
        T mutable = editor.itemEditor.clone(item, true);
        modifiedToOriginal.put(mutable, item);
        items.set(rowIndex, mutable);
        return mutable;
      }
    }

    private boolean isMutable(T item) {
      return modifiedToOriginal.containsKey(item);
    }

    public boolean isModified(@NotNull List<T> oldItems) {
      if (items.size() != oldItems.size()) {
        return true;
      }
      else {
        for (int i = 0, size = items.size(); i < size; i++) {
          if (!items.get(i).equals(oldItems.get(i))) {
            return true;
          }
        }
      }

      return false;
    }

    @NotNull
    public List<T> apply() {
      if (modifiedToOriginal.isEmpty()) {
        return items;
      }

      @SuppressWarnings("unchecked")
      final ColumnInfo<T, Object>[] columns = getColumnInfos();
      modifiedToOriginal.forEachEntry(new TObjectObjectProcedure<T, T>() {
        @Override
        public boolean execute(T newItem, @Nullable T oldItem) {
          if (oldItem == null) {
            // it is added item, we don't need to sync
            return true;
          }

          for (ColumnInfo<T, Object> column : columns) {
            if (column.isCellEditable(newItem)) {
              column.setValue(oldItem, column.valueOf(newItem));
            }
          }

          if (editor.itemEditor instanceof DialogItemEditor) {
            ((DialogItemEditor<T>)editor.itemEditor).applyEdited(oldItem, newItem);
          }

          items.set(ContainerUtil.indexOfIdentity(items, newItem), oldItem);
          return true;
        }
      });

      modifiedToOriginal.clear();
      return items;
    }
  }

  public abstract static class EditableColumnInfo<Item, Aspect> extends ColumnInfo<Item, Aspect> {
    public EditableColumnInfo(@NotNull String name) {
      super(name);
    }

    public EditableColumnInfo() {
      super("");
    }

    @Override
    public boolean isCellEditable(Item item) {
      return true;
    }
  }

  @NotNull
  public JComponent createComponent() {
    return toolbarDecorator.addExtraAction(
      new ToolbarDecorator.ElementActionButton(IdeBundle.message("button.copy"), PlatformIcons.COPY_ICON) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          TableUtil.stopEditing(table);

          List<T> selectedItems = table.getSelectedObjects();
          if (selectedItems.isEmpty()) {
            return;
          }

          for (T item : selectedItems) {
            model.addRow(itemEditor.clone(item, false));
          }

          table.requestFocus();
          TableUtil.updateScroller(table, false);
        }
      }
    ).createPanel();
  }

  @Override
  public T createElement() {
    try {
      Constructor<T> constructor = itemEditor.getItemClass().getDeclaredConstructor();
      try {
        constructor.setAccessible(true);
      }
      catch (SecurityException e) {
        return itemEditor.getItemClass().newInstance();
      }
      return constructor.newInstance();
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean canCreateElement() {
    return true;
  }

  public boolean isModified(@NotNull List<T> oldItems) {
    return model.isModified(oldItems);
  }

  @NotNull
  public List<T> apply() {
    return model.apply();
  }

  public void reset(@NotNull List<T> items) {
    model.setItems(new ArrayList<T>(items));
  }
}