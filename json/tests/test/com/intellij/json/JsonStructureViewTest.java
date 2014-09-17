package com.intellij.json;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.lang.LanguageStructureViewBuilder;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Consumer;
import com.intellij.util.PlatformIcons;

import static com.intellij.testFramework.PlatformTestUtil.assertTreeEqual;

/**
 * @author Mikhail Golubev
 */
public class JsonStructureViewTest extends JsonTestCase {

  private void doTest(final String expected) {
    myFixture.configureByFile("structureView/" + getTestName(false) + ".json");
    myFixture.testStructureView(new Consumer<StructureViewComponent>() {
      @Override
      public void consume(StructureViewComponent component) {
        assertTreeEqual(component.getTree(), expected);
      }
    });
  }

  public void testSimpleStructure() {
    doTest("-SimpleStructure.json\n" +
           " aaa\n" +
           " -bbb\n" +
           "  ccc\n");
  }

  // Moved from JavaScript

  public void testJsonStructure() {
    myFixture.configureByFile("structureView/SimpleStructure.json");

    final StructureViewBuilder builder = LanguageStructureViewBuilder.INSTANCE.getStructureViewBuilder(myFixture.getFile());
    assertNotNull(builder);
    StructureViewComponent component = null;
    try {
      final FileEditor editor = FileEditorManager.getInstance(getProject()).getSelectedEditor(myFixture.getFile().getVirtualFile());
      component = (StructureViewComponent)builder.createStructureView(editor, myFixture.getProject());
      final StructureViewModel model = component.getTreeModel();

      TreeElement[] children = model.getRoot().getChildren();
      assertEquals(2, children.length);
      assertEquals("aaa", children[0].getPresentation().getPresentableText());
      assertEquals(PlatformIcons.PROPERTY_ICON, children[0].getPresentation().getIcon(false));
      assertEquals("bbb", children[1].getPresentation().getPresentableText());
      assertEquals(PlatformIcons.PROPERTY_ICON, children[1].getPresentation().getIcon(false));

      children = children[1].getChildren();
      assertEquals(1, children.length);
      assertEquals("ccc", children[0].getPresentation().getPresentableText());
      assertEquals(PlatformIcons.PROPERTY_ICON, children[0].getPresentation().getIcon(false));
    }
    finally {
      if (component != null) {
        Disposer.dispose(component);
      }
    }
  }
}
