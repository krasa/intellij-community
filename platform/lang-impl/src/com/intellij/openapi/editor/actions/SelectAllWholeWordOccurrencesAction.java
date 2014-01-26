package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;

/**
 * @author Vojtech Krasa
 */
public class SelectAllWholeWordOccurrencesAction extends SelectAllOccurrencesAction {
  @Override
  public EditorActionHandler getHandler() {
    return new Handler();
  }

  protected static class Handler extends SelectAllOccurrencesAction.Handler {
    @Override
    public void execute(Editor editor, DataContext dataContext) {
      final EditorSettings settings = editor.getSettings();
         final boolean camelWords = settings.isCamelWords();
         try {
           settings.setCamelWords(false);
           super.execute(editor, dataContext);
      
         }
         finally {
           settings.setCamelWords(camelWords);
         }
    }

    @Override
    protected boolean wholeWordsOnly() {
      return true;
    }
  }
}
