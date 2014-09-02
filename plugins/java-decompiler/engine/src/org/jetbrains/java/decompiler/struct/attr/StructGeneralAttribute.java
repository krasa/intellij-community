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
package org.jetbrains.java.decompiler.struct.attr;

import org.jetbrains.java.decompiler.struct.consts.ConstantPool;

import java.io.DataOutputStream;
import java.io.IOException;

/*
    attribute_info {
    	u2 attribute_name_index;
    	u4 attribute_length;
    	u1 info[attribute_length];
    }
*/

public class StructGeneralAttribute {

  public static final String ATTRIBUTE_CODE = "Code";
  public static final String ATTRIBUTE_INNER_CLASSES = "InnerClasses";
  public static final String ATTRIBUTE_SIGNATURE = "Signature";
  public static final String ATTRIBUTE_ANNOTATION_DEFAULT = "AnnotationDefault";
  public static final String ATTRIBUTE_EXCEPTIONS = "Exceptions";
  public static final String ATTRIBUTE_ENCLOSING_METHOD = "EnclosingMethod";
  public static final String ATTRIBUTE_RUNTIME_VISIBLE_ANNOTATIONS = "RuntimeVisibleAnnotations";
  public static final String ATTRIBUTE_RUNTIME_INVISIBLE_ANNOTATIONS = "RuntimeInvisibleAnnotations";
  public static final String ATTRIBUTE_RUNTIME_VISIBLE_PARAMETER_ANNOTATIONS = "RuntimeVisibleParameterAnnotations";
  public static final String ATTRIBUTE_RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS = "RuntimeInvisibleParameterAnnotations";
  public static final String ATTRIBUTE_RUNTIME_VISIBLE_TYPE_ANNOTATIONS = "RuntimeVisibleTypeAnnotations";
  public static final String ATTRIBUTE_RUNTIME_INVISIBLE_TYPE_ANNOTATIONS = "RuntimeInvisibleTypeAnnotations";
  public static final String ATTRIBUTE_LOCAL_VARIABLE_TABLE = "LocalVariableTable";
  public static final String ATTRIBUTE_CONSTANT_VALUE = "ConstantValue";
  public static final String ATTRIBUTE_BOOTSTRAP_METHODS = "BootstrapMethods";
  public static final String ATTRIBUTE_SYNTHETIC = "Synthetic";
  public static final String ATTRIBUTE_DEPRECATED = "Deprecated";


  // *****************************************************************************
  // private fields
  // *****************************************************************************

  protected int attribute_name_index;

  protected byte[] info;

  protected String name;

  // *****************************************************************************
  // public methods
  // *****************************************************************************

  public void writeToStream(DataOutputStream out) throws IOException {
    out.writeShort(attribute_name_index);
    out.writeInt(info.length);
    if (info.length > 0) {
      out.write(info);
    }
  }

  public void initContent(ConstantPool pool) {
    name = pool.getPrimitiveConstant(attribute_name_index).getString();
  }

  public static StructGeneralAttribute getMatchingAttributeInstance(int nameindex, String attrname) {

    StructGeneralAttribute attr;

    if (ATTRIBUTE_INNER_CLASSES.equals(attrname)) {
      attr = new StructInnerClassesAttribute();
    }
    else if (ATTRIBUTE_CONSTANT_VALUE.equals(attrname)) {
      attr = new StructConstantValueAttribute();
    }
    else if (ATTRIBUTE_SIGNATURE.equals(attrname)) {
      attr = new StructGenericSignatureAttribute();
    }
    else if (ATTRIBUTE_ANNOTATION_DEFAULT.equals(attrname)) {
      attr = new StructAnnDefaultAttribute();
    }
    else if (ATTRIBUTE_EXCEPTIONS.equals(attrname)) {
      attr = new StructExceptionsAttribute();
    }
    else if (ATTRIBUTE_ENCLOSING_METHOD.equals(attrname)) {
      attr = new StructEnclosingMethodAttribute();
    }
    else if (ATTRIBUTE_RUNTIME_VISIBLE_ANNOTATIONS.equals(attrname) ||
             ATTRIBUTE_RUNTIME_INVISIBLE_ANNOTATIONS.equals(attrname)) {
      attr = new StructAnnotationAttribute();
    }
    else if (ATTRIBUTE_RUNTIME_VISIBLE_PARAMETER_ANNOTATIONS.equals(attrname) ||
             ATTRIBUTE_RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS.equals(attrname)) {
      attr = new StructAnnotationParameterAttribute();
    }
    else if (ATTRIBUTE_RUNTIME_VISIBLE_TYPE_ANNOTATIONS.equals(attrname) ||
             ATTRIBUTE_RUNTIME_INVISIBLE_TYPE_ANNOTATIONS.equals(attrname)) {
      attr = new StructAnnotationTypeAttribute();
    }
    else if (ATTRIBUTE_LOCAL_VARIABLE_TABLE.equals(attrname)) {
      attr = new StructLocalVariableTableAttribute();
    }
    else if (ATTRIBUTE_BOOTSTRAP_METHODS.equals(attrname)) {
      attr = new StructBootstrapMethodsAttribute();
    }
    else if (ATTRIBUTE_SYNTHETIC.equals(attrname) || ATTRIBUTE_DEPRECATED.equals(attrname)) {
      attr = new StructGeneralAttribute();
    }
    else {
      // unsupported attribute
      return null;
    }

    attr.setAttribute_name_index(nameindex);
    return attr;
  }

  // *****************************************************************************
  // getter and setter methods
  // *****************************************************************************

  public byte[] getInfo() {
    return info;
  }

  public void setInfo(byte[] info) {
    this.info = info;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public int getAttribute_name_index() {
    return attribute_name_index;
  }

  public void setAttribute_name_index(int attribute_name_index) {
    this.attribute_name_index = attribute_name_index;
  }
}
