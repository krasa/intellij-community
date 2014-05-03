package com.intellij.execution.filters;

import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.Assert.assertThat;

public class OrderedFiltersTest {

  protected Filter myFilterA = createNonOrderableFilter("a");
  protected Filter myFilterB = createNonOrderableFilter("b");
  protected Filter myFilterC = createNonOrderableFilter("c");
  protected Filter myFilterD = createNonOrderableFilter("d");
  protected Filter myFilterA_100 = createOrderableFilter("a", 100);
  protected Filter myFilterA_minus_100 = createOrderableFilter("a", -100);
  protected Filter myFilterB_minus_100 = createOrderableFilter("b", -100);

  private Filter createOrderableFilter(String a, int i) {
    return new MyFilter(a, i);
  }

  @Test
  public void testAddNonOrderableFilter() throws Exception {
    final OrderedFilters orderedFilters = new OrderedFilters();
    orderedFilters.addFilter(myFilterA, OrderableFilter.DEFAULT_FILTER_ORDER);
    orderedFilters.addFilter(myFilterB, OrderableFilter.DEFAULT_FILTER_ORDER);
    assertThat(orderedFilters.getFilters(), contains(myFilterA, myFilterB));

    orderedFilters.addFilter(myFilterC, -1);
    assertThat(orderedFilters.getFilters(), contains(myFilterC, myFilterA, myFilterB));

    orderedFilters.addFilter(myFilterD, -1);
    assertThat(orderedFilters.getFilters(), contains(myFilterC, myFilterD, myFilterA, myFilterB));
  }

  @Test
  public void testAddOrderableFilter() throws Exception {
    final OrderedFilters orderedFilters = new OrderedFilters();

    orderedFilters.addFilter(myFilterA, OrderableFilter.DEFAULT_FILTER_ORDER);
    orderedFilters.addFilter(myFilterA_minus_100, OrderableFilter.DEFAULT_FILTER_ORDER);
    assertThat(orderedFilters.getFilters(), contains(myFilterA_minus_100, myFilterA));

    orderedFilters.addFilter(myFilterA_100, OrderableFilter.DEFAULT_FILTER_ORDER);
    assertThat(orderedFilters.getFilters(), contains(myFilterA_minus_100, myFilterA, myFilterA_100));


    orderedFilters.addFilter(myFilterB_minus_100, OrderableFilter.DEFAULT_FILTER_ORDER);
    assertThat(orderedFilters.getFilters(), contains(myFilterA_minus_100, myFilterB_minus_100, myFilterA, myFilterA_100));


    orderedFilters.addFilter(myFilterB, OrderableFilter.DEFAULT_FILTER_ORDER);
    assertThat(orderedFilters.getFilters(), contains(myFilterA_minus_100, myFilterB_minus_100, myFilterA, myFilterB, myFilterA_100));
  }

  public Filter createNonOrderableFilter(String s) {
    return new MyNonOrderableFilter(s);
  }

  private static class MyFilter implements Filter, OrderableFilter {
    private String name;
    private int order;

    public MyFilter(String a, int i) {
      name = a;
      order = i;
    }

    @Nullable
    @Override
    public Result applyFilter(String line, int entireLength) {
      return null;
    }

    @Override
    public int getOrder() {
      return order;
    }

    @Override
    public String toString() {
      return "MyFilter{" +
             "myA='" + name + '\'' +
             ", order=" + order +
             '}';
    }
  }

  private static class MyNonOrderableFilter implements Filter {
    private String name;

    public MyNonOrderableFilter(String s) {
      name = s;
    }

    @Nullable
    @Override
    public Result applyFilter(String line, int entireLength) {
      return null;
    }

    @Override
    public String toString() {
      return "MyNonOrderableFilter{" +
             "myS='" + name + '\'' +
             '}';
    }
  }
}