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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class OrderedFilters {
  private static final Logger log = Logger.getInstance(OrderedFilters.class.getName());

  private List<OrderableFilter> myFilters = new ArrayList<OrderableFilter>();
  private List<Filter> myOrderedFiltersCache;

  public OrderedFilters() {
  }

  public void addFilter(Filter filter, int defaultFilterOrder) {
    OrderableFilter orderableFilter;
    if (filter instanceof OrderableFilter) {
      orderableFilter = (OrderableFilter)filter;
    }
    else {
      orderableFilter = new OrderableFilterWrapper(filter, defaultFilterOrder);
    }
    checkDuplicates(filter, myFilters);

    myFilters.add(orderableFilter);


    sort(myFilters);

    myOrderedFiltersCache = null;
  }

  private void checkDuplicates(Filter filter, List<OrderableFilter> orderedFiltersCache) {
    for (OrderableFilter filter1 : orderedFiltersCache) {
      if (unwrap(filter1).getClass().equals(filter.getClass())) {
        log.warn("Filter of class " + filter.getClass().getName() + " already present");
      }
    }
  }


  private List<Filter> unwrap(List<OrderableFilter> filters) {
    final List<Filter> list = new ArrayList<Filter>(filters.size());
    for (OrderableFilter filter : filters) {
      list.add(unwrap(filter));
    }
    return list;
  }

  private Filter unwrap(OrderableFilter filter) {
    final Filter filter1;
    if (filter instanceof OrderableFilterWrapper) {
      filter1 = ((OrderableFilterWrapper)filter).myFilter;
    }
    else {
      filter1 = (Filter)filter;
    }
    return filter1;
  }

  private void sort(List<OrderableFilter> filters) {
    OrderableFilterComparator.sort(filters);
  }

  public boolean isEmpty() {
    return myFilters.isEmpty();
  }

  public List<Filter> getFilters() {
    if (myOrderedFiltersCache == null) {
      myOrderedFiltersCache = unwrap(myFilters);
      if (log.isDebugEnabled()) {
        log.debug("created filters list:\n" + Arrays.toString(myOrderedFiltersCache.toArray()).replace(", ", "\n, "));
      }
    }
    return myOrderedFiltersCache;
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