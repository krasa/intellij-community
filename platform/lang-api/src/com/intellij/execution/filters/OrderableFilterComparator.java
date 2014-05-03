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
package com.intellij.execution.filters;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author Vojtech Krasa
 */
public class OrderableFilterComparator implements Comparator<OrderableFilter> {
  public static final OrderableFilterComparator INSTANCE = new OrderableFilterComparator();

  public static void sort(List<OrderableFilter> list) {
    if (list.size() > 1) {
      Collections.sort(list, INSTANCE);
    }
  }

  @Override
  public int compare(OrderableFilter o1, OrderableFilter o2) {
    //not using Integer.compareTo to avoid unnecessary object creation.
    int i1 = o1.getOrder();
    int i2 = o2.getOrder();
    return (i1 < i2) ? -1 : (i1 > i2) ? 1 : 0;
  }
}
