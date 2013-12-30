/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.impl.editorActions;

import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.MultiEditAction;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class SpaceHandler extends TypedActionHandlerBase {
  public SpaceHandler(TypedActionHandler originalHandler) {
    super(originalHandler);
  }

  @Override
  public void execute(@NotNull final Editor editor, final char charTyped, @NotNull final DataContext dataContext) {
    if (charTyped == ' ') {
      Project project = CommonDataKeys.PROJECT.getData(dataContext);
      if (project != null) {
        TemplateManagerImpl templateManager = (TemplateManagerImpl)TemplateManager.getInstance(project);
        if (templateManager != null && templateManager.startTemplate(editor, TemplateSettings.SPACE_CHAR)) {
          return;
        }
      }
    }

    if (myOriginalHandler != null) {
      if (LookupManager.getActiveLookup(editor) != null) {
        MultiEditAction.executeWithMultiEdit(new Runnable() {
          @Override
          public void run() {
            myOriginalHandler.execute(editor, charTyped, dataContext);
          }
        }, editor, null);
      }
      else {
        myOriginalHandler.execute(editor, charTyped, dataContext);
      }
    }
  }
}
