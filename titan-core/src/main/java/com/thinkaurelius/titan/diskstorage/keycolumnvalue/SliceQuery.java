package com.thinkaurelius.titan.diskstorage.keycolumnvalue;

import com.google.common.base.Preconditions;
import com.thinkaurelius.titan.diskstorage.Entry;
import com.thinkaurelius.titan.diskstorage.EntryList;
import com.thinkaurelius.titan.diskstorage.StaticBuffer;
import com.thinkaurelius.titan.diskstorage.util.BufferUtil;
import com.thinkaurelius.titan.diskstorage.util.StaticArrayEntryList;
import com.thinkaurelius.titan.graphdb.query.BackendQuery;
import com.thinkaurelius.titan.graphdb.query.BaseQuery;
import org.apache.commons.lang.builder.HashCodeBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * Queries for a slice of data identified by a start point (inclusive) and end point (exclusive).
 * Returns all {@link StaticBuffer}s that lie in this range up to the given limit.
 * <p/>
 * If a SliceQuery is marked <i>static</i> it is expected that the result set does not change.
 *
 * @author Matthias Broecheler (me@matthiasb.com)
 */

public class SliceQuery extends BaseQuery implements BackendQuery<SliceQuery> {

    private final StaticBuffer sliceStart;
    private final StaticBuffer sliceEnd;

    public SliceQuery(final StaticBuffer sliceStart, final StaticBuffer sliceEnd) {
        assert sliceStart != null && sliceEnd != null;

        this.sliceStart = sliceStart;
        this.sliceEnd = sliceEnd;
    }

    public SliceQuery(final SliceQuery query) {
        this(query.getSliceStart(), query.getSliceEnd());
        setLimit(query.getLimit());
    }

    /**
     * The start of the slice is considered to be inclusive
     *
     * @return The StaticBuffer denoting the start of the slice
     */
    public StaticBuffer getSliceStart() {
        return sliceStart;
    }

    /**
     * The end of the slice is considered to be exclusive
     *
     * @return The StaticBuffer denoting the end of the slice
     */
    public StaticBuffer getSliceEnd() {
        return sliceEnd;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(sliceStart).append(sliceEnd).append(getLimit()).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other)
            return true;

        if (other == null && !getClass().isInstance(other))
            return false;

        SliceQuery oth = (SliceQuery) other;
        return sliceStart.equals(oth.sliceStart)
                && sliceEnd.equals(oth.sliceEnd)
                && getLimit() == oth.getLimit();
    }

    public boolean subsumes(SliceQuery oth) {
        Preconditions.checkNotNull(oth);
        if (this == oth) return true;
        if (oth.getLimit() > getLimit()) return false;
        else if (!hasLimit()) //the interval must be subsumed
            return sliceStart.compareTo(oth.sliceStart) <= 0 && sliceEnd.compareTo(oth.sliceEnd) >= 0;
        else //this the result might be cutoff due to limit, the start must be the same
            return sliceStart.compareTo(oth.sliceStart) == 0 && sliceEnd.compareTo(oth.sliceEnd) >= 0;
    }

    //TODO: make this more efficient by using reuseIterator() on otherResult
    public EntryList getSubset(final SliceQuery otherQuery, final EntryList otherResult) {
        assert otherQuery.subsumes(this);
        int pos = Collections.binarySearch(otherResult, sliceStart);
        if (pos < 0) pos = -pos - 1;

        List<Entry> result = new ArrayList<Entry>();
        for (; pos < otherResult.size() && result.size() < getLimit(); pos++) {
            Entry e = otherResult.get(pos);
            if (e.getColumnAs(StaticBuffer.STATIC_FACTORY).compareTo(sliceEnd) < 0) result.add(e);
            else break;
        }
        return StaticArrayEntryList.of(result);
    }

    public boolean contains(StaticBuffer buffer) {
        return sliceStart.compareTo(buffer)<=0 && sliceEnd.compareTo(buffer)>0;
    }

    public static StaticBuffer pointRange(StaticBuffer point) {
        return BufferUtil.nextBiggerBuffer(point);
    }

    @Override
    public SliceQuery setLimit(int limit) {
        Preconditions.checkArgument(!hasLimit());
        super.setLimit(limit);
        return this;
    }

    @Override
    public SliceQuery updateLimit(int newLimit) {
        return new SliceQuery(sliceStart, sliceEnd).setLimit(newLimit);
    }

}
