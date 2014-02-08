package edu.tufts.eaftan.hprofparser.handler.examples.statisticscollectinghandler;

import com.google.common.primitives.Ints;

import edu.tufts.eaftan.hprofparser.handler.NullRecordHandler;

import edu.tufts.eaftan.hprofparser.parser.datastructures.Constant;
import edu.tufts.eaftan.hprofparser.parser.datastructures.InstanceField;
import edu.tufts.eaftan.hprofparser.parser.datastructures.Static;
import edu.tufts.eaftan.hprofparser.parser.datastructures.Type;
import edu.tufts.eaftan.hprofparser.parser.datastructures.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes basic statistics about a heap dump.  For example, the number of instances of each
 * type and the total bytes used by instances of that type.
 * 
 * <p>Note that we intentionally ignore array and object header sizes because they are VM
 * dependent.
 */
public class StatisticsCollectingHandler extends NullRecordHandler {
  private Map<Long, TypeInfo> classMap = new HashMap<>();
  private Map<Long, String> stringMap = new HashMap<>();
  private Map<String, TypeInfo> arrayInfoMap = new HashMap<>();

  @Override
  public void stringInUTF8(long id, String data) {
    stringMap.put(id, data);
  }
  
  @Override
  public void loadClass(int classSerialNum, long classObjId, int stackTraceSerialNum,
      long classNameStringId) {
    ClassInfo classInfo = new ClassInfo();
    classInfo.className = stringMap.get(classNameStringId);
    classMap.put(classObjId, classInfo);
  }
  
  @Override
  public void classDump(long classObjId, int stackTraceSerialNum, long superClassObjId, 
      long classLoaderObjId, long signersObjId, long protectionDomainObjId, long reserved1,
      long reserved2, int instanceSize, Constant[] constants, Static[] statics,
      InstanceField[] instanceFields) {
    ClassInfo classInfo = (ClassInfo) classMap.get(classObjId);
    classInfo.instanceSize = instanceSize;
  }


  @Override
  public void instanceDump(long objId, int stackTraceSerialNum, long classObjId,
      Value<?>[] instanceFieldValues) {
    ClassInfo classInfo = (ClassInfo) classMap.get(classObjId);
    classInfo.instanceCount++;
  }

  @Override
  public void objArrayDump(long objId, int stackTraceSerialNum, long elemClassObjId, long[] elems) {
    String typeDescriptor = "[" + classMap.get(elemClassObjId).className;
    int length = elems != null ? elems.length : 0;  
    recordArrayInstance(typeDescriptor, length * Type.OBJ.sizeInBytes());
  }

  @Override
  public void primArrayDump(long objId, int stackTraceSerialNum, byte hprofElemType, Value<?>[] elems) {
    Type elemType = Type.hprofTypeToEnum(hprofElemType);
    String typeDescriptor = "[" + elemType.toString();
    int length = elems != null ? elems.length : 0;  
    recordArrayInstance(typeDescriptor, length * elemType.sizeInBytes());
  }

  private void recordArrayInstance(String typeDescriptor, int bytes) {
    ArrayInfo arrayInfo = (ArrayInfo) arrayInfoMap.get(typeDescriptor);
    if (arrayInfo == null) {
      arrayInfo = new ArrayInfo();
      arrayInfo.className = typeDescriptor;
      arrayInfoMap.put(typeDescriptor, arrayInfo);
    }
    arrayInfo.instanceCount++;
    arrayInfo.totalSize += bytes;
  }
  
  @Override
  public void finished() {
    Comparator<TypeInfo> totalSizeComparator = new Comparator<TypeInfo>() {
      @Override
      public int compare(TypeInfo cls1, TypeInfo cls2) {
        return Ints.checkedCast(cls2.totalSize() - cls1.totalSize());
      }
    };
    
    List<TypeInfo> typeInfoList = new ArrayList<>(classMap.values());
    typeInfoList.addAll(arrayInfoMap.values());
    Collections.sort(typeInfoList, totalSizeComparator);
    for (TypeInfo typeInfo : typeInfoList) {
      if (typeInfo.instanceCount == 0) {
        continue;
      }
      System.out.println(typeInfo.toString());
    }
  }
   

}
