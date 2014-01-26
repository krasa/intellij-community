package org.jetbrains.plugins.javaFX.sceneBuilder;

import com.intellij.openapi.diagnostic.Logger;
import com.oracle.javafx.scenebuilder.kit.editor.EditorController;
import com.oracle.javafx.scenebuilder.kit.editor.drag.source.AbstractDragSource;
import com.oracle.javafx.scenebuilder.kit.editor.panel.content.ContentPanelController;
import com.oracle.javafx.scenebuilder.kit.editor.panel.hierarchy.treeview.HierarchyTreeViewController;
import com.oracle.javafx.scenebuilder.kit.editor.panel.inspector.InspectorPanelController;
import com.oracle.javafx.scenebuilder.kit.editor.panel.library.LibraryPanelController;
import com.oracle.javafx.scenebuilder.kit.fxom.FXOMDocument;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.SplitPane;
import javafx.scene.input.DataFormat;

import javax.swing.*;
import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

/**
 * @author Alexander Lobas
 */
public class SceneBuilderKitWrapper {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.javaFX.sceneBuilder.SceneBuilderKitWrapper");

  public static JComponent create(final String path) throws Exception {
    Field identifier = DataFormat.class.getDeclaredField("identifier");
    identifier.setAccessible(true);
    identifier.set(AbstractDragSource.INTERNAL_DATA_FORMAT, Collections
      .unmodifiableSet(new HashSet<String>(Arrays.asList("application/scene.builder.internal"))));

    FXMLLoader.setDefaultClassLoader(SceneBuilderKitWrapper.class.getClassLoader());

    final JFXPanel panel = new JFXPanel();
    Platform.runLater(new Runnable() {
      @Override
      public void run() {
        EditorController editor = new EditorController();
        HierarchyTreeViewController componentTree = new HierarchyTreeViewController(editor);
        ContentPanelController canvas = new ContentPanelController(editor);
        InspectorPanelController propertyTable = new InspectorPanelController(editor);
        LibraryPanelController palette = new LibraryPanelController(editor);

        try {
          URL fxmlURL = new File(path).toURI().toURL();
          String fxmlText = FXOMDocument.readContentFromURL(fxmlURL);
          editor.setFxmlTextAndLocation(fxmlText, fxmlURL);
        }
        catch (Throwable e) {
          LOG.error(e);
        }

        SplitPane leftPane = new SplitPane();
        leftPane.setOrientation(Orientation.VERTICAL);
        leftPane.getItems().addAll(palette.getPanelRoot(), componentTree.getPanelRoot());
        leftPane.setDividerPositions(0.5, 0.5);

        SplitPane mainPane = new SplitPane();

        mainPane.getItems().addAll(leftPane, canvas.getPanelRoot(), propertyTable.getPanelRoot());
        mainPane.setDividerPositions(0, 0.5, 1);

        panel.setScene(new Scene(mainPane, 900, 600));
      }
    });

    return panel;
  }
}