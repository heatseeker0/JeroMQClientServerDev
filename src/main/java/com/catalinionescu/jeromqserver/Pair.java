package com.catalinionescu.jeromqserver;

import java.io.Serializable;

/**
 * A pair of two elements. If the elements are immutable this pair becomes immutable too.
 * 
 * Implements {@link Comparable} and {@link Serializable}.
 * 
 * @author Catalin Ionescu
 * 
 * @param <X> First element
 * @param <Y> Second element
 */
public class Pair<X, Y> implements Comparable<Pair<X, Y>>, Serializable {
    private static final long serialVersionUID = 1L;
    private final X first;
    private final Y second;

    /**
     * Creates a pair of two elements.
     * 
     * @param first First element
     * @param second Second element
     */
    public Pair(X first, Y second) {
        this.first = first;
        this.second = second;
    }

    /**
     * Gets the first element of the pair.
     * 
     * @return First element
     */
    public X getFirst() {
        return first;
    }

    /**
     * Gets the second element of the pair.
     * 
     * @return Second element
     */
    public Y getSecond() {
        return second;
    }

    /**
     * Returns a String representation of this pair using the format (first, second).
     */
    @Override
    public String toString() {
        return String.format("(%s,%s)", first.toString(), second.toString());
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Pair)) {
            return false;
        }
        if (other == this) {
            return true;
        }
        return ((((Pair<?, ?>) other).first == null && this.first == null) || ((Pair<?, ?>) other).first.equals(this.first)) &&
                ((((Pair<?, ?>) other).second == null && this.second == null) || ((Pair<?, ?>) other).second.equals(this.second));
    }

    @Override
    public int hashCode() {
        int result = 31 + ((first == null) ? 0 : first.hashCode());
        result = 31 * result + ((second == null) ? 0 : second.hashCode());
        return result;
    }

    /**
     * Assumes X and Y are of comparable type and non null. If not, throws {@link ClassCastException} or {@link NullPointerException} as per
     * {@link Comparable#compareTo(T o)}.
     */
    @SuppressWarnings("unchecked")
    @Override
    public int compareTo(Pair<X, Y> other) {
        int result = ((Comparable<X>) this.getFirst()).compareTo(other.getFirst());
        if (result == 0)
            return ((Comparable<Y>) this.getSecond()).compareTo(other.getSecond());
        return result;
    }
}
