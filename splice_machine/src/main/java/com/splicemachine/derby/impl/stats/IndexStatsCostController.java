package com.splicemachine.derby.impl.stats;

import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.services.io.FormatableBitSet;
import com.splicemachine.db.iapi.sql.compile.CostEstimate;
import com.splicemachine.db.iapi.store.access.StoreCostResult;
import com.splicemachine.db.iapi.types.DataValueDescriptor;
import com.splicemachine.derby.impl.store.access.BaseSpliceTransaction;
import com.splicemachine.derby.impl.store.access.SpliceTransactionManager;
import com.splicemachine.derby.impl.store.access.StatsStoreCostController;
import com.splicemachine.derby.impl.store.access.base.OpenSpliceConglomerate;
import com.splicemachine.derby.impl.store.access.base.SpliceConglomerate;
import com.splicemachine.pipeline.exception.Exceptions;
import com.splicemachine.si.api.TxnView;
import com.splicemachine.stats.PartitionStatistics;
import com.splicemachine.stats.TableStatistics;
import java.util.concurrent.ExecutionException;

/**
 * @author Scott Fines
 *         Date: 3/10/15
 */
public class IndexStatsCostController extends StatsStoreCostController {
    private TableStatistics baseTableStatistics;
    private int[] indexColToHeapColMap;

    public IndexStatsCostController(int[] indexColToHeapColMap,OpenSpliceConglomerate baseConglomerate) throws StandardException {
        /*
         * This looks a bit weird, because baseConglomerate is actually the index conglomerate,
         * so our super class is actually looking at the cost to just scan in the index. We
         * need to use the base table statistics in order to get the proper column
         * selectivity, so we lookup the base table statistics here, but we use the index
         * statistics when estimating the fetch cost.
         *
         */
        super(baseConglomerate);
        BaseSpliceTransaction bst = (BaseSpliceTransaction)baseConglomerate.getTransaction();
        TxnView txn = bst.getActiveStateTxn();
        long conglomId = baseConglomerate.getIndexConglomerate();
        this.indexColToHeapColMap = new int[indexColToHeapColMap.length];
        for(int i=0;i<indexColToHeapColMap.length;i++){
            this.indexColToHeapColMap[i] = indexColToHeapColMap[i]-1;
        }

        try {
            this.baseTableStatistics = StatisticsStorage.getPartitionStore().getStatistics(txn, conglomId);
        } catch (ExecutionException e) {
            throw Exceptions.parseException(e);
        }
    }


    @Override
    public void getScanCost(int scan_type,
                            long row_count,
                            int group_size,
                            boolean forUpdate,
                            FormatableBitSet scanColumnList,
                            DataValueDescriptor[] template,
                            DataValueDescriptor[] startKeyValue,
                            int startSearchOperator,
                            DataValueDescriptor[] stopKeyValue,
                            int stopSearchOperator,
                            boolean reopen_scan,
                            int access_type,
                            StoreCostResult cost_result) throws StandardException {
        /*
         * We have two possibilities:
         *
         * 1. Covering index. In this case, we just delegate to the super's cost behavior (since
         * we are a base scan anyway)
         * 2. Non-covering index. In that case, for each row, we need to add in the cost of performing
         * a network read, so we multiply the super's cost behavior by the remote read latency
         */
        SpliceConglomerate conglomerate = (SpliceConglomerate)baseConglomerate.getConglomerate();
        //we know keys exist because we are an index
        int[] keyColumnEncodingOrder = conglomerate.getColumnOrdering();
        boolean isCovering;
        if(scanColumnList!=null) {
            FormatableBitSet missingColumns = (FormatableBitSet) scanColumnList.clone();
            for (int keyColumn : keyColumnEncodingOrder) {
                missingColumns.clear(keyColumn);
            }
            isCovering = missingColumns.getNumBitsSet()<=0;
        }else{
            /*
             * We are scanning all the data in the table, so we just have to determine if this
             * index has all of the columns or not.
             */
            long heapConglomerate = baseConglomerate.getIndexConglomerate();
            SpliceConglomerate heapConglom =
                    (SpliceConglomerate)((SpliceTransactionManager) baseConglomerate.getTransactionManager()).findConglomerate(heapConglomerate);
            int[] heapFormatIds = heapConglom.getFormat_ids();
            int[] indexFormatIds = conglomerate.getFormat_ids();
            /*
             * We know that the last column in any index table is the RowLocation for the base row,
             * so that means that, if we were a fully covering index, we would have 1 more than the heapFormatIds.
             */
            isCovering = indexFormatIds.length-1==heapFormatIds.length;
        }
        super.estimateCost(baseTableStatistics,
                startKeyValue, startSearchOperator,
                stopKeyValue, stopSearchOperator,
                indexColToHeapColMap,
                (CostEstimate) cost_result,!isCovering);
    }

    @Override
    protected double costScaleFactor(PartitionStatistics partStats) {
        return conglomerateStatistics.remoteReadLatency()/conglomerateStatistics.localReadLatency();
    }
}
