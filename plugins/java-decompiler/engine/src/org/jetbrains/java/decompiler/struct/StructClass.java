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
package org.jetbrains.java.decompiler.struct;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.struct.attr.StructGeneralAttribute;
import org.jetbrains.java.decompiler.struct.consts.ConstantPool;
import org.jetbrains.java.decompiler.struct.consts.PrimitiveConstant;
import org.jetbrains.java.decompiler.struct.lazy.LazyLoader;
import org.jetbrains.java.decompiler.util.DataInputFullStream;
import org.jetbrains.java.decompiler.util.InterpreterUtil;
import org.jetbrains.java.decompiler.util.VBStyleCollection;

import java.io.*;

/*
    ClassFile {
    	u4 magic;
    	u2 minor_version;
    	u2 major_version;
    	u2 constant_pool_count;
    	cp_info constant_pool[constant_pool_count-1];
    	u2 access_flags;
    	u2 this_class;
    	u2 super_class;
    	u2 interfaces_count;
    	u2 interfaces[interfaces_count];
    	u2 fields_count;
    	field_info fields[fields_count];
    	u2 methods_count;
    	method_info methods[methods_count];
    	u2 attributes_count;
    	attribute_info attributes[attributes_count];
    }
*/

public class StructClass {

  // *****************************************************************************
  // public fields
  // *****************************************************************************

  public int minor_version;

  public int major_version;

  public int access_flags;

  public int this_class;

  public int super_class;

  public PrimitiveConstant thisClass;

  public PrimitiveConstant superClass;

  public String qualifiedName;


  // *****************************************************************************
  // private fields
  // *****************************************************************************

  private ConstantPool pool;

  private int[] interfaces;

  private String[] interfaceNames;

  private VBStyleCollection<StructField, String> fields = new VBStyleCollection<StructField, String>();

  private VBStyleCollection<StructMethod, String> methods = new VBStyleCollection<StructMethod, String>();

  private VBStyleCollection<StructGeneralAttribute, String> attributes = new VBStyleCollection<StructGeneralAttribute, String>();

  private boolean own = true;

  private LazyLoader loader;

  // *****************************************************************************
  // constructors
  // *****************************************************************************

  public StructClass(String filename, boolean own, LazyLoader loader) throws IOException {
    this(new FileInputStream(filename), own, loader);
  }

  public StructClass(InputStream inStream, boolean own, LazyLoader loader) throws IOException {
    this(new DataInputFullStream(inStream), own, loader);
  }

  public StructClass(DataInputFullStream inStream, boolean own, LazyLoader loader) throws IOException {
    this.own = own;
    this.loader = loader;

    initStruct(inStream);
  }

  // *****************************************************************************
  // public methods
  // *****************************************************************************

  public boolean hasField(String name, String descriptor) {
    return getField(name, descriptor) != null;
  }

  public StructField getField(String name, String descriptor) {
    return fields.getWithKey(InterpreterUtil.makeUniqueKey(name, descriptor));
  }

  public StructMethod getMethod(String key) {
    return methods.getWithKey(key);
  }

  public StructMethod getMethod(String name, String descriptor) {
    return methods.getWithKey(InterpreterUtil.makeUniqueKey(name, descriptor));
  }

  public void writeToFile(File file) throws IOException {
    DataOutputStream out = new DataOutputStream(new FileOutputStream(file));
    writeToOutputStream(out);
    out.close();
  }

  public void writeToOutputStream(DataOutputStream out) throws IOException {

    out.writeInt(0xCAFEBABE);
    out.writeShort(minor_version);
    out.writeShort(major_version);

    getPool().writeToOutputStream(out);

    out.writeShort(access_flags);
    out.writeShort(this_class);
    out.writeShort(super_class);

    out.writeShort(interfaces.length);
    for (int i = 0; i < interfaces.length; i++) {
      out.writeShort(interfaces[i]);
    }

    out.writeShort(fields.size());
    for (int i = 0; i < fields.size(); i++) {
      fields.get(i).writeToStream(out);
    }

    out.writeShort(methods.size());
    for (int i = 0; i < methods.size(); i++) {
      methods.get(i).writeToStream(out);
    }

    out.writeShort(attributes.size());
    for (StructGeneralAttribute attr : attributes) {
      attr.writeToStream(out);
    }
  }

  public String getInterface(int i) {
    return interfaceNames[i];
  }

  public void releaseResources() {
    if (loader != null) {
      pool = null;
    }
  }

  // *****************************************************************************
  // private methods
  // *****************************************************************************

  private void initStruct(DataInputFullStream in) throws IOException {

    in.skip(4);

    this.minor_version = in.readUnsignedShort();
    this.major_version = in.readUnsignedShort();

    pool = new ConstantPool(in);

    this.access_flags = in.readUnsignedShort();

    this_class = in.readUnsignedShort();
    thisClass = pool.getPrimitiveConstant(this_class);
    qualifiedName = thisClass.getString();

    super_class = in.readUnsignedShort();
    superClass = pool.getPrimitiveConstant(super_class);

    // interfaces
    int length = in.readUnsignedShort();
    int[] arrInterfaces = new int[length];
    String[] arrInterfaceNames = new String[length];

    for (int i = 0; i < length; i++) {
      arrInterfaces[i] = in.readUnsignedShort();
      arrInterfaceNames[i] = pool.getPrimitiveConstant(arrInterfaces[i]).getString();
    }
    this.interfaces = arrInterfaces;
    this.interfaceNames = arrInterfaceNames;

    // fields
    VBStyleCollection<StructField, String> lstFields = new VBStyleCollection<StructField, String>();
    length = in.readUnsignedShort();
    for (int i = 0; i < length; i++) {
      StructField field = new StructField();
      field.access_flags = in.readUnsignedShort();
      field.name_index = in.readUnsignedShort();
      field.descriptor_index = in.readUnsignedShort();

      field.initStrings(pool, this_class);

      field.setAttributes(readAttributes(in));

      lstFields.addWithKey(field, InterpreterUtil.makeUniqueKey(field.getName(), field.getDescriptor()));
    }
    this.fields = lstFields;

    // methods
    length = in.readUnsignedShort();
    for (int i = 0; i < length; i++) {
      StructMethod meth = new StructMethod(in, own, this);

      //if(qualifiedName.endsWith("JUnitStatusLine") && !meth.getName().equals("onProcessStarted") && !meth.getName().startsWith("access")) {
      //if(!meth.getName().equals("run")) {
      //	continue;
      //}

      methods.addWithKey(meth, InterpreterUtil.makeUniqueKey(meth.getName(), meth.getDescriptor()));
    }

    // attributes
    this.attributes = readAttributes(in);


    // release memory
    if (loader != null) {
      pool = null;
    }
  }

  private VBStyleCollection<StructGeneralAttribute, String> readAttributes(DataInputFullStream in) throws IOException {

    VBStyleCollection<StructGeneralAttribute, String> lstAttribute = new VBStyleCollection<StructGeneralAttribute, String>();

    int length = in.readUnsignedShort();
    for (int i = 0; i < length; i++) {
      int attr_nameindex = in.readUnsignedShort();
      String attrname = pool.getPrimitiveConstant(attr_nameindex).getString();

      StructGeneralAttribute attr = StructGeneralAttribute.getMatchingAttributeInstance(attr_nameindex, attrname);

      if (attr != null) {
        byte[] arr = new byte[in.readInt()];
        in.readFull(arr);
        attr.setInfo(arr);

        attr.initContent(pool);
        lstAttribute.addWithKey(attr, attr.getName());
      }
      else {
        in.skip(in.readInt());
      }
    }

    return lstAttribute;
  }


  // *****************************************************************************
  // getter and setter methods
  // *****************************************************************************

  public ConstantPool getPool() {

    if (pool == null && loader != null) {
      pool = loader.loadPool(qualifiedName);
    }

    return pool;
  }

  public int[] getInterfaces() {
    return interfaces;
  }

  public String[] getInterfaceNames() {
    return interfaceNames;
  }

  public VBStyleCollection<StructMethod, String> getMethods() {
    return methods;
  }

  public VBStyleCollection<StructField, String> getFields() {
    return fields;
  }

  public VBStyleCollection<StructGeneralAttribute, String> getAttributes() {
    return attributes;
  }

  public boolean isOwn() {
    return own;
  }

  public LazyLoader getLoader() {
    return loader;
  }

  public boolean isVersionGE_1_5() {
    return (major_version > 48 || (major_version == 48 && minor_version > 0)); // FIXME: check second condition
  }

  public boolean isVersionGE_1_7() {
    return (major_version >= 51);
  }

  public int getBytecodeVersion() {
    switch (major_version) {
      case 52:
        return CodeConstants.BYTECODE_JAVA_8;
      case 51:
        return CodeConstants.BYTECODE_JAVA_7;
      case 50:
        return CodeConstants.BYTECODE_JAVA_6;
      case 49:
        return CodeConstants.BYTECODE_JAVA_5;
    }

    return CodeConstants.BYTECODE_JAVA_LE_4;
  }
}
