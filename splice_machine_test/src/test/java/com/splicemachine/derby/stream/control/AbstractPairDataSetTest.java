package com.splicemachine.derby.stream.control;

import com.google.common.base.Optional;
import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.sql.execute.ExecRow;
import com.splicemachine.derby.iapi.sql.execute.SpliceOperation;
import com.splicemachine.derby.stream.BaseStreamTest;
import com.splicemachine.derby.stream.function.RowComparator;
import com.splicemachine.derby.stream.function.SpliceFlatMapFunction;
import com.splicemachine.derby.stream.function.SpliceFunction;
import com.splicemachine.derby.stream.function.SpliceFunction2;
import com.splicemachine.derby.stream.iapi.DataSet;
import com.splicemachine.derby.stream.iapi.PairDataSet;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import scala.Tuple2;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;

/**
 * Created by jleach on 4/15/15.
 */
public abstract class AbstractPairDataSetTest extends BaseStreamTest {

    protected abstract PairDataSet<ExecRow, ExecRow> getTenRows();
    protected abstract PairDataSet<ExecRow, ExecRow> getEvenRows();


    @Test
    public void testValues() {
        PairDataSet<ExecRow, ExecRow> pairDataSet = getTenRows();
        Iterator<ExecRow> it = pairDataSet.values().toLocalIterator();
        int i = 0;
        while (it.hasNext()) {
            i++;
            Assert.assertEquals("grabbed the incorrect value from the map", 10, it.next().nColumns());
        }
        Assert.assertEquals("grabbed the incorrect number of rows", 10, i);
    }

    @Test
    public void testKeys() {
        PairDataSet<ExecRow, ExecRow> pairDataSet = getTenRows();
        Iterator<ExecRow> it = pairDataSet.keys().toLocalIterator();
        int i = 0;
        while (it.hasNext()) {
            i++;
            Assert.assertEquals("grabbed the incorrect value from the map", 1, it.next().nColumns());
        }
        Assert.assertEquals("grabbed the incorrect number of rows", 10, i);
    }


    @Test
    @Ignore // ignoring to let Spark ITs complete
    public void testReduceByKey() throws StandardException {
        PairDataSet<ExecRow, ExecRow> pairDataSet = getTenRows();
        PairDataSet<ExecRow, ExecRow> transformedDS = pairDataSet.reduceByKey(new ReduceByKeyFunction());
        Assert.assertEquals("records not reduced", 2, transformedDS.keys().collect().size());
        Assert.assertEquals("records not reduced", 2, transformedDS.values().collect().size());
        Iterator<ExecRow> it = transformedDS.values().toLocalIterator();
        while (it.hasNext()) {
            ExecRow er = it.next();
            Assert.assertEquals("value not 5", 5, er.getColumn(1).getInt());
        }
    }

    @Test
    public void testCogroup() throws StandardException {
        PairDataSet<ExecRow, ExecRow> set1 = getTenRows();
        PairDataSet<ExecRow, ExecRow> set2 = getTenRows();
        PairDataSet<ExecRow, Tuple2<Iterable<ExecRow>, Iterable<ExecRow>>> returnValues = set1.cogroup(set2);
        Iterator<Tuple2<Iterable<ExecRow>, Iterable<ExecRow>>> it = returnValues.values().toLocalIterator();
        int i = 0;
        while (it.hasNext()) {
            Tuple2<Iterable<ExecRow>,Iterable<ExecRow>> groupedTuples = it.next();
            Iterator it1 = groupedTuples._1.iterator();
            int j = 0;
            while (it1.hasNext()) {
                it1.next();
                j++;
            }
            Assert.assertEquals("MissingRecords", 5, j);
            Iterator it2 = groupedTuples._2.iterator();
            int k = 0;
            while (it2.hasNext()) {
                it2.next();
                k++;
            }
            Assert.assertEquals("MissingRecords", 5, k);
            i++;
        }
        Assert.assertEquals("MissingRecords", 2, i);
    }

    @Test
    public void testMap() throws StandardException {
        PairDataSet<ExecRow, ExecRow> set1 = getTenRows();
        DataSet<Integer> keyDs = set1.map(new MapKeyFunction());
        DataSet<Integer> valueDS = set1.map(new MapValueFunction());
        Iterator<Integer> itKey = keyDs.toLocalIterator();
        Iterator<Integer> itValue = keyDs.toLocalIterator();
        int i = 0;
        while (itKey.hasNext()) {
            Integer foo = itKey.next();
            Integer value = itValue.next();
            i++;
            Assert.assertTrue("wrong value", foo.intValue() < 3);
        }
        Assert.assertEquals("MissingRecords", 10, i);
    }

    @Test
    public void testSortByKey() throws StandardException {
        PairDataSet<ExecRow, ExecRow> set1 = getTenRows();
        Iterator<ExecRow> rowIT = set1.sortByKey(new RowComparator()).keys().toLocalIterator();
        int i = 0;
        while (rowIT.hasNext()) {
            if (i < 5)
                Assert.assertEquals("Incorrect Ordering of Rows", 0, rowIT.next().getColumn(1).getInt());
            else
                Assert.assertEquals("Incorrect Ordering of Rows", 1, rowIT.next().getColumn(1).getInt());
            i++;
        }
    }

    @Test
    public void testHashLeftOuterJoin() throws StandardException {
        PairDataSet<ExecRow, ExecRow> set1 = getTenRows();
        PairDataSet<ExecRow, ExecRow> set2 = getEvenRows();
        Iterator<Tuple2<ExecRow,Optional<ExecRow>>> it = set1.hashLeftOuterJoin(set2).values().toLocalIterator();
        int i =0;
        while (it.hasNext()) {
            Tuple2<ExecRow,Optional<ExecRow>> tuple = it.next();
            if (tuple._1.getColumn(1).getInt()%2==0)
                Assert.assertTrue("Join Issue", tuple._2.isPresent());
            else
                Assert.assertTrue("Join Issue", !tuple._2.isPresent());
            i++;
        }
        Assert.assertEquals("Incorrect Number of Rows", 30, i);
    }

    @Test
    public void testHashJoin() throws StandardException {
        PairDataSet<ExecRow, ExecRow> set1 = getTenRows();
        PairDataSet<ExecRow, ExecRow> set2 = getEvenRows();
        Iterator<Tuple2<ExecRow,ExecRow>> it = set1.hashJoin(set2).values().toLocalIterator();
        int i =0;
        while (it.hasNext()) {
            Tuple2<ExecRow,ExecRow> tuple = it.next();
            if (tuple._1.getColumn(1).getInt()%2==0) {
                Assert.assertTrue("Join Issue", true);
                Assert.assertEquals("Join Issue", 0, tuple._2.getColumn(1).getInt()%2);
            }
            else
                Assert.assertTrue("Join Issue", false);
            i++;
        }
        Assert.assertEquals("Incorrect Number of Rows", 25, i);
    }

    @Test
    public void testBroadcastLeftOuterJoin() throws StandardException {
        PairDataSet<ExecRow, ExecRow> set1 = getTenRows();
        PairDataSet<ExecRow, ExecRow> set2 = getEvenRows();
        Iterator<Tuple2<ExecRow,Optional<ExecRow>>> it = set1.broadcastLeftOuterJoin(set2).values().toLocalIterator();
        int i =0;
        while (it.hasNext()) {
            Tuple2<ExecRow,Optional<ExecRow>> tuple = it.next();
            if (tuple._1.getColumn(1).getInt()%2==0)
                Assert.assertTrue("Join Issue", tuple._2.isPresent());
            else
                Assert.assertTrue("Join Issue", !tuple._2.isPresent());
            i++;
        }
        Assert.assertEquals("Incorrect Number of Rows", 30, i);
    }

    @Test
    public void testBroadcastJoin() throws StandardException {
        PairDataSet<ExecRow, ExecRow> set1 = getTenRows();
        PairDataSet<ExecRow, ExecRow> set2 = getEvenRows();
        Iterator<Tuple2<ExecRow,ExecRow>> it = set1.broadcastJoin(set2).values().toLocalIterator();
        int i =0;
        while (it.hasNext()) {
            Tuple2<ExecRow,ExecRow> tuple = it.next();
            if (tuple._1.getColumn(1).getInt()%2==0) {
                Assert.assertTrue("Join Issue", true);
                Assert.assertEquals("Join Issue", 0,tuple._2.getColumn(1).getInt()%2);
            }
            else
                Assert.assertTrue("Join Issue", false);
            i++;
        }
        Assert.assertEquals("Incorrect Number of Rows", 25, i);
    }

    @Test
    public void testSubtractByKey() throws StandardException {
        PairDataSet<ExecRow, ExecRow> set1 = getTenRows();
        PairDataSet<ExecRow, ExecRow> set2 = getEvenRows();
        Iterator<ExecRow> it = set1.subtractByKey(set2).values().toLocalIterator();
        int i =0;
        while (it.hasNext()) {
            ExecRow row = it.next();
            if (row.getColumn(1).getInt()%2==0)
                Assert.assertTrue("Not Subtracted", false);
            else
                Assert.assertEquals("Join Issue", 1,row.getColumn(1).getInt()%2);
            i++;
        }
        Assert.assertEquals("Incorrect Number of Rows", 5, i);
    }

    @Test
    public void testBroadcastSubtractByKey() throws StandardException {
        PairDataSet<ExecRow, ExecRow> set1 = getTenRows();
        PairDataSet<ExecRow, ExecRow> set2 = getEvenRows();
        Iterator<ExecRow> it = set1.broadcastSubtractByKey(set2).values().toLocalIterator();
        int i =0;
        while (it.hasNext()) {
            ExecRow row = it.next();
            if (row.getColumn(1).getInt()%2==0)
                Assert.assertTrue("Not Subtracted", false);
            else
                Assert.assertEquals("Join Issue", 1,row.getColumn(1).getInt()%2);
            i++;
        }
        Assert.assertEquals("Incorrect Number of Rows", 5, i);
    }

    @Test
    public void testFlatMap() throws StandardException {
        PairDataSet<ExecRow, ExecRow> set1 = getTenRows();
        Iterator<Integer> it = set1.flatmap(new FlatMapFunction()).toLocalIterator();
        int i =0;
        while (it.hasNext()) {
            Assert.assertEquals("flatMap Has Incorrect Rows",0,it.next().intValue()%2);
            i++;
        }
        Assert.assertEquals("Incorrect Number of Rows", 10, i);
    }

    @Test
    public void testBroadcastCogroup() throws StandardException {
        PairDataSet<ExecRow, ExecRow> set1 = getTenRows();
        PairDataSet<ExecRow, ExecRow> set2 = getTenRows();
        PairDataSet<ExecRow, Tuple2<Iterable<ExecRow>, Iterable<ExecRow>>> returnValues = set1.cogroup(set2);
        Iterator<Tuple2<Iterable<ExecRow>, Iterable<ExecRow>>> it = returnValues.values().toLocalIterator();
        int i = 0;
        while (it.hasNext()) {
            Tuple2<Iterable<ExecRow>,Iterable<ExecRow>> groupedTuples = it.next();
            Iterator it1 = groupedTuples._1.iterator();
            int j = 0;
            while (it1.hasNext()) {
                it1.next();
                j++;
            }
            Assert.assertEquals("MissingRecords", 5, j);
            Iterator it2 = groupedTuples._2.iterator();
            int k = 0;
            while (it2.hasNext()) {
                it2.next();
                k++;
            }
            Assert.assertEquals("MissingRecords", 5, k);
            i++;
        }
        Assert.assertEquals("MissingRecords", 2, i);
    }

    public static class MapKeyFunction extends SpliceFunction<SpliceOperation, Tuple2<ExecRow, ExecRow>, Integer> {
        public MapKeyFunction() {

        }
        @Override
        public Integer call(Tuple2<ExecRow, ExecRow> tuple) throws Exception {
            return tuple._1.getColumn(1).getInt();
        }
    };

    public static class MapValueFunction extends SpliceFunction<SpliceOperation, Tuple2<ExecRow, ExecRow>, Integer> {
        public MapValueFunction() {}
        @Override
        public Integer call(Tuple2<ExecRow, ExecRow> tuple) throws Exception {
            return tuple._1.getColumn(2).getInt();
        }
    }

    public static class FlatMapFunction extends SpliceFlatMapFunction<SpliceOperation, Tuple2<ExecRow, ExecRow>, Integer> {

        public FlatMapFunction() {

        }
        @Override
        public Iterable<Integer> call(Tuple2<ExecRow, ExecRow> tuple) throws Exception {
            if (tuple._1.getColumn(1).getInt()%2==0)
                return Arrays.asList(tuple._2.getColumn(1).getInt(),tuple._2.getColumn(2).getInt());
            return Collections.EMPTY_LIST;
        }
    }

    public static class ReduceByKeyFunction extends SpliceFunction2<SpliceOperation, ExecRow, ExecRow, ExecRow> {

        public ReduceByKeyFunction() {

        }

        @Override
        public ExecRow call(ExecRow execRow, ExecRow execRow2) throws Exception {
            if (execRow == null)
                return execRow2;
            if (execRow2 == null)
                return execRow;
            ExecRow row = getExecRow(1, 1);
            row.getColumn(1).setValue(execRow.getColumn(1).getInt() + 1);
            return row;
        }
    }

}