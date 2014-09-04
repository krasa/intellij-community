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
package org.jetbrains.java.decompiler.main;

import org.jetbrains.java.decompiler.main.ClassesProcessor.ClassNode;
import org.jetbrains.java.decompiler.main.collectors.CounterContainer;
import org.jetbrains.java.decompiler.main.extern.IBytecodeProvider;
import org.jetbrains.java.decompiler.main.extern.IDecompilatSaver;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.modules.renamer.IdentifierConverter;
import org.jetbrains.java.decompiler.struct.IDecompiledData;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructContext;
import org.jetbrains.java.decompiler.struct.lazy.LazyLoader;

import java.io.BufferedWriter;
import java.io.StringWriter;
import java.util.Map;


public class Fernflower implements IDecompiledData {

  private StructContext structContext;
  private ClassesProcessor classesProcessor;

  public Fernflower(IBytecodeProvider provider, IDecompilatSaver saver, Map<String, Object> propertiesCustom) {
    structContext = new StructContext(saver, this, new LazyLoader(provider));
    DecompilerContext.initContext(propertiesCustom);
    DecompilerContext.setCounterContainer(new CounterContainer());
  }

  public void decompileContext() {
    if (DecompilerContext.getOption(IFernflowerPreferences.RENAME_ENTITIES)) {
      new IdentifierConverter().rename(structContext);
    }

    classesProcessor = new ClassesProcessor(structContext);

    DecompilerContext.setClassProcessor(classesProcessor);
    DecompilerContext.setStructContext(structContext);

    structContext.saveContext();
  }

  public void clearContext() {
    DecompilerContext.setCurrentContext(null);
  }

  public String getClassEntryName(StructClass cl, String entryName) {
    ClassNode node = classesProcessor.getMapRootClasses().get(cl.qualifiedName);
    if (node.type != ClassNode.CLASS_ROOT) {
      return null;
    }
    else {
      if (DecompilerContext.getOption(IFernflowerPreferences.RENAME_ENTITIES)) {
        String simple_classname = cl.qualifiedName.substring(cl.qualifiedName.lastIndexOf('/') + 1);
        return entryName.substring(0, entryName.lastIndexOf('/') + 1) + simple_classname + ".java";
      }
      else {
        return entryName.substring(0, entryName.lastIndexOf(".class")) + ".java";
      }
    }
  }

  public StructContext getStructContext() {
    return structContext;
  }

  public String getClassContent(StructClass cl) {
    try {
      StringWriter writer = new StringWriter();
      classesProcessor.writeClass(cl, new BufferedWriter(writer));
      return writer.toString();
    }
    catch (Throwable ex) {
      DecompilerContext.getLogger().writeMessage("Class " + cl.qualifiedName + " couldn't be fully decompiled.", ex);
      return null;
    }
  }
}
