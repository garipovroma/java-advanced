package info.kgeorgiy.ja.garipov.arrayset;

import java.util.*;

public class ArraySet<T> extends AbstractSet<T> implements NavigableSet<T> {
    private final ReversibleList<T> reversibleList;
    private final Comparator<? super T> comparator;

    public ArraySet(Collection<? extends T> collection, Comparator<? super  T> comparator) {
        TreeSet<T> treeSet = new TreeSet<T>(comparator);
        treeSet.addAll(collection);
        this.reversibleList = new ReversibleList<T>(new ArrayList<T>(treeSet), false);
        this.comparator = comparator;
    }

    private ArraySet(ReversibleList<T> reversibleList, Comparator<? super T> comparator) {
        this.reversibleList = reversibleList;
        this.comparator = comparator;
    }

    public ArraySet(SortedSet<T> sortedSet) {
        this(new ReversibleList<T>(new ArrayList<T>(sortedSet), false), sortedSet.comparator());
    }

    public ArraySet(Collection<? extends T> collection) {
        this(collection, null);
    }

    public ArraySet() {
        this(Collections.emptyList());
    }

    private T getOrNull(int i) {
        return (i < reversibleList.size() && i >= 0 ? reversibleList.get(i) : null);
    }

    public int binarySearch(T t) {
        return Collections.binarySearch(reversibleList, t, comparator);
    }

    public int getIndex(T t, boolean equality, boolean lower) {
        int id = binarySearch(t);
        if (id >= 0) {
            return id + (equality ? 0 : lower ? -1 : 1);
        } else {
            return -id + (lower ? -2 : -1);
        }
    }

    public T findNearestElement(T t, boolean equality, boolean lower) {
        return getOrNull(getIndex(t, equality, lower));
    }

    @Override
    public T lower(T t) {
        return findNearestElement(t, false,  true);
    }

    @Override
    public T floor(T t) {
        return findNearestElement(t, true, true);
    }

    @Override
    public T ceiling(T t) {
        return findNearestElement(t, true, false);
    }

    @Override
    public T higher(T t) {
        return findNearestElement(t, false, false);
    }

    @Override
    public T pollFirst() {
        throw new UnsupportedOperationException();
    }

    @Override
    public T pollLast() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int size() {
        return reversibleList.size();
    }

    @Override
    public boolean isEmpty() {
        return reversibleList.isEmpty();
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean contains(Object o) {
        return Collections.binarySearch(reversibleList, (T) o, comparator) >= 0;
    }

    @Override
    public Iterator<T> iterator() {
        return reversibleList.iterator();
    }

    // NOTE: no need to override containsAll
    // NOTE: fixed
    @Override
    public NavigableSet<T> descendingSet() {
        return new ArraySet<T>(new ReversibleList<T>(reversibleList, true), Collections.reverseOrder(comparator));
    }

    @Override
    public Iterator<T> descendingIterator() {
        return descendingSet().iterator();
    }

    private NavigableSet<T> subSetByIndexes(int from, int to) {
        if (from > to) {
            return new ArraySet<T>(Collections.emptyList(), comparator);
        }
        // RevList(UnmodList(SubList(RevList())))
        // NOTE: you keep on layering RevList every time you call subset
        // NOTE: fixed

        // :NOTE-2: not-fixed =(
        return new ArraySet<T>(new ReversibleList<T>(reversibleList, from, to), comparator);
    }

    @Override
    @SuppressWarnings("unchecked")
    public NavigableSet<T> subSet(T from, boolean fromInclusive, T to, boolean toInclusive) {
        if ((comparator != null && comparator.compare(from, to) > 0)
                ||  (comparator == null && ((Comparable<T>)(from)).compareTo(to) > 0)) {
            throw new IllegalArgumentException();
        }
        return subSetByIndexes(getIndex(from, fromInclusive, false), getIndex(to, toInclusive, true) + 1);
    }

    @Override
    public NavigableSet<T> headSet(T to, boolean inclusive) {
        return subSetByIndexes(0, getIndex(to, !inclusive, false));
    }

    @Override
    public NavigableSet<T> tailSet(T from, boolean inclusive) {
        return subSetByIndexes(getIndex(from, inclusive, false), size());
    }

    @Override
    public Comparator<? super T> comparator() {
        return comparator;
    }

    @Override
    public SortedSet<T> subSet(T from, T to) {
        return subSet(from, true, to, false);
    }

    @Override
    public SortedSet<T> headSet(T to) {
        return headSet(to, false);
    }

    @Override
    public SortedSet<T> tailSet(T from) {
        return tailSet(from, true);
    }

    @Override
    public T first() {
        if (reversibleList.isEmpty()) {
            throw new NoSuchElementException();
        }
        return reversibleList.get(0);
    }

    @Override
    public T last() {
        if (reversibleList.isEmpty()) {
            throw new NoSuchElementException();
        }
        return reversibleList.get(reversibleList.size() - 1);
    }
}
