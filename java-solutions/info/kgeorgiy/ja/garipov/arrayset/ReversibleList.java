package info.kgeorgiy.ja.garipov.arrayset;

import java.util.*;

class ReversibleList<T> extends AbstractList<T> implements RandomAccess {
    private final List<T> list;
    private final boolean reversed;

    public ReversibleList(List<T> list, boolean reversed) {
        this.list = Collections.unmodifiableList(list);
        this.reversed = reversed;
    }

    public ReversibleList(ReversibleList<T> reversibleList, boolean reversed) {
        this.list = reversibleList.getList();
        this.reversed = reversibleList.isReversed() ^ reversed;
    }

    public ReversibleList(ReversibleList<T> reversibleList, int from, int to) {
        // SubList
        this(reversibleList.list.subList(from, to), reversibleList.isReversed());
    }

    // NOTE: no need to override
    // NOTE: fixed
    public List<T> getList() {
        return list;
    }

    public boolean isReversed() {
        return reversed;
    }

    @Override
    public T get(int i) {
        return (reversed ? list.get(list.size() - 1 - i) : list.get(i));
    }

    @Override
    public int size() {
        return list.size();
    }
}

