package com.splicemachine.derby.impl.sql.complextypes;


import com.splicemachine.derby.test.framework.SpliceDataWatcher;
import com.splicemachine.derby.test.framework.SpliceSchemaWatcher;
import com.splicemachine.derby.test.framework.SpliceTableWatcher;
import com.splicemachine.derby.test.framework.SpliceWatcher;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.Description;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Time;
import java.sql.Timestamp;

/**
 *
 * IT to test SQL Array Handling in the execution stack
 *
 */
public class SQLArrayIT {

    private static final SpliceWatcher classWatcher = new SpliceWatcher();
    private static final SpliceSchemaWatcher schema = new SpliceSchemaWatcher(SQLArrayIT.class.getSimpleName().toUpperCase());

    private static final SpliceTableWatcher charDelete = new SpliceTableWatcher("CHAR_DELETE",schema.schemaName,"(c char(10))");
    private static final SpliceTableWatcher intDecimalBetween = new SpliceTableWatcher("BETWEEN_TEST",schema.schemaName,"(d DECIMAL, i int)");
    private static final SpliceTableWatcher load = new SpliceTableWatcher("TIME",schema.schemaName,"(a int generated by default as identity primary key, b TIME, c DATE, d TIME,e TIMESTAMP)");

    private static final int LOAD_ROW_COUNT=6000;
    @ClassRule
    public static final TestRule rule = RuleChain.outerRule(classWatcher)
            .around(schema)
            .around(charDelete)
            .around(intDecimalBetween)
            .around(load)
            .around(new SpliceDataWatcher() {
                @Override
                protected void starting(Description description) {
                    try(PreparedStatement ps=classWatcher.prepareStatement(String.format("insert into %s values (?,?)", intDecimalBetween))){
                        ps.setBigDecimal(1, new BigDecimal(1));
                        ps.setInt(2, 1);
                        ps.execute();
                        ps.setBigDecimal(1, new BigDecimal(2));
                        ps.setInt(2, 2);
                        ps.execute();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }).around(new SpliceDataWatcher(){
                @Override
                protected void starting(Description description){
                    try(PreparedStatement ps = classWatcher.prepareStatement(String.format("insert into %s (b,c,d,e) values (?,?,?,?)",load))){
                        int batchSize = LOAD_ROW_COUNT/100;
                        int count = 0;
                        for(int i=0;i<LOAD_ROW_COUNT;i++){
                            ps.setTime(1,new Time(System.currentTimeMillis()));
                            ps.setDate(2,new Date(System.currentTimeMillis()));
                            ps.setTime(3,new Time(System.currentTimeMillis()));
                            ps.setTimestamp(4,new Timestamp(System.currentTimeMillis()));
                            ps.addBatch();

                            count++;
                            if((count % batchSize)==0)
                                ps.executeBatch();
                        }
                    }catch(Exception e){
                        throw new RuntimeException(e);
                    }
                }
            });


    @Test
    public void testStatistics() {

    }

    @Test
    public void testQualifier() {

    }

    @Test
    public void testJDBCRead() {

    }

    @Test
    public void testAggregation() {

    }

    @Test
    public void testUpdate() {

    }

    @Test
    public void testDelete() {

    }

    /**
     *
     * sdfsdfsdfsd,sdfdsfdsfsdf,[]
     * Varchar,Varchar,int array
     * sdfsdfsdfsd,sdfdsfdsfsdf,[0,0,0]
     * sdfsdfsdfsd,sdfdsfdsfsdf,[0,0,0]
     *
     * where col1[1] = "1"
     *
     */
    @Test
    public void testImportArrays() {

    }

    @Test
    public void testExportArrays() {

    }

    @Test
    public void testJoinOnArrayPosition() {

    }

    @Test
    public void testSortOnArrayPosition() {

    }

    @Test
    public void testJoinOnArray() {

    }

    @Test
    public void testSortOnArray() {

    }

    @Test
    public void testIndex() {

    }

    @Test
    public void testPrimaryKey() {

    }
    // [Array] select * from foo.col1 where
    // [Struct] select * from foo inner join foo.col2 where
    @Test
    public void testRI() {

    }

}