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

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class OrderedFilters {
  private static final Logger log = Logger.getInstance(OrderedFilters.class.getName());

  private List<OrderableFilter> myFilters = new ArrayList<OrderableFilter>();
  @Nullable
  private List<Filter> myOrderedFiltersCache;

  public void addFilter(Filter filter, int defaultFilterOrder) {
    checkDuplicates(filter, myFilters);

    myFilters.add(toOrderableFilter(filter, defaultFilterOrder));

    myOrderedFiltersCache = null;
  }

  @NotNull
  public List<Filter> getFilters() {
    if (myOrderedFiltersCache == null) {
      sort(myFilters);
      myOrderedFiltersCache = unwrap(myFilters);
      if (log.isDebugEnabled()) {
        log.debug("filters:\n" + Arrays.toString(myOrderedFiltersCache.toArray()).replace(", ", "\n, "));
      }
    }
    return myOrderedFiltersCache;
  }

  public boolean isEmpty() {
    return myFilters.isEmpty();
  }

  private void checkDuplicates(Filter filter, List<OrderableFilter> filters) {
    for (OrderableFilter filter1 : filters) {
      if (unwrap(filter1).getClass().equals(filter.getClass())) {
        log.warn("Filter of class " + filter.getClass().getName() + " already present");
      }
    }
  }

  private Filter unwrap(OrderableFilter filter) {
    final Filter result;
    if (filter instanceof OrderableFilterWrapper) {
      result = ((OrderableFilterWrapper)filter).myFilter;
    }
    else {
      result = (Filter)filter;
    }
    return result;
  }

  private OrderableFilter toOrderableFilter(Filter filter, int order) {
    OrderableFilter orderableFilter;
    if (filter instanceof OrderableFilter) {
      orderableFilter = (OrderableFilter)filter;
    }
    else {
      orderableFilter = new OrderableFilterWrapper(filter, order);
    }
    return orderableFilter;
  }

  private void sort(List<OrderableFilter> filters) {
    OrderableFilterComparator.sort(filters);
  }

  private List<Filter> unwrap(List<OrderableFilter> filters) {
    final List<Filter> list = new ArrayList<Filter>(filters.size());
    for (OrderableFilter filter : filters) {
      list.add(unwrap(filter));
    }
    return list;
  }

  /**
   * @author Vojtech Krasa
   */
  private static class OrderableFilterWrapper implements OrderableFilter {
    private Filter myFilter;
    private int myOrder;

    public OrderableFilterWrapper(Filter filter, int order) {
      myFilter = filter;
      myOrder = order;
    }

    @Override
    public int getOrder() {
      return myOrder;
    }
  }
}