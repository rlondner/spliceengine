package com.splicemachine.si.api;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.splicemachine.constants.SIConstants;
import com.splicemachine.si.data.api.SDataLib;
import com.splicemachine.si.data.api.STableWriter;
import com.splicemachine.si.data.hbase.HDataLib;
import com.splicemachine.si.data.hbase.HTableWriter;
import com.splicemachine.si.data.hbase.IHTable;
import com.splicemachine.si.impl.DataStore;
import com.splicemachine.si.impl.SITransactionReadController;
import com.splicemachine.si.impl.SITransactor;
import com.splicemachine.si.impl.SystemClock;
import com.splicemachine.si.jmx.ManagedTransactor;
import com.splicemachine.si.jmx.TransactorStatus;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.regionserver.HRegion;
import com.splicemachine.si.txn.SpliceTimestampSource;
import com.splicemachine.utils.ZkUtils;
import org.apache.hadoop.hbase.client.*;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Used to construct a transactor object that is bound to the HBase types and that provides SI functionality. This is
 * the main entry point for applications using SI. Keeps track of a global, static instance that is shared by all callers.
 * Also defines the various configuration options and plugins for the transactor instance.
 */
public class HTransactorFactory extends SIConstants {
    //replaced with the TxnStore and TxnSupplier interfaces
//    private final static Map<Long, ImmutableTransaction> immutableCache = CacheMap.makeCache(true);
//    private final static Map<Long, ActiveTransactionCacheEntry> activeCache = CacheMap.makeCache(true);
//    private final static Map<Long, Transaction> cache = CacheMap.makeCache(true);
//    private final static Map<Long, Transaction> committedCache = CacheMap.makeCache(true);
//    private final static Map<Long, Transaction> failedCache = CacheMap.makeCache(true);
//    private final static Map<PermissionArgs, Byte> permissionCache = CacheMap.makeCache(true);

		private static volatile boolean initialized;
    private static volatile ManagedTransactor managedTransactor;
		private static volatile TransactionManager transactionManager;
		private static volatile ClientTransactor clientTransactor;
		private static volatile SITransactionReadController< Get, Scan, Delete, Put> readController;
//		private static volatile RollForwardQueueFactory<byte[], HbRegion> rollForwardQueueFactory;
//		private static volatile TransactionStore transactionStore;
//		private static volatile DataStore dataStore;
//		private static volatile TxnStore txnStore;
//		private static volatile TxnSupplier txnSupplier;
//    private static volatile AsyncReadResolver asyncReadResolver;

    public static void setTransactor(ManagedTransactor managedTransactorToUse) {
        managedTransactor = managedTransactorToUse;
    }

    public static TransactorStatus getTransactorStatus() {
        initializeIfNeeded();
        return managedTransactor;
    }

    public static TransactionManager getTransactionManager() {
        initializeIfNeeded();
        return transactionManager;
    }

    public static Transactor<IHTable, Mutation,Put> getTransactor() {
        initializeIfNeeded();
        return managedTransactor.getTransactor();
    }

    public static TransactionReadController<Get,Scan> getTransactionReadController(){
        initializeIfNeeded();
        return readController;
    }

    @SuppressWarnings("unchecked")
    private static void initializeIfNeeded(){
        if(initialized) return;

        synchronized (HTransactorFactory.class) {
            if(initialized) return;
            if(managedTransactor!=null && transactionManager !=null){
                //it was externally created
                initialized = true;
                return;
            }

            // TODO: permanently purge this later (leave commented out for now while we test more)
            // TimestampSource timestampSource = new ZooKeeperStatTimestampSource(ZkUtils.getRecoverableZooKeeper(),zkSpliceTransactionPath);
//						if(timestampSource==null)
//								timestampSource = new SpliceTimestampSource(ZkUtils.getRecoverableZooKeeper());

            SDataLib dataLib = new HDataLib();
            final STableWriter writer = new HTableWriter();
            ManagedTransactor builderTransactor = new ManagedTransactor();
//						transactionStore = new TransactionStore(transactionSchema, dataLib, reader, writer,
//										immutableCache, activeCache, cache, committedCache, failedCache, permissionCache, SIConstants.committingPause, builderTransactor);


//						if(transactionManager ==null)
//								transactionManager = new SITransactionManager(transactionStore,timestampSource,builderTransactor);
            DataStore ds = TxnDataStore.getDataStore();
            TxnLifecycleManager tc = TransactionLifecycle.getLifecycleManager();
//						if(rollForwardQueueFactory ==null)
//								rollForwardQueueFactory = new HBaseRollForwardQueueFactory(Providers.basicProvider(transactionStore),
//												Providers.basicProvider(dataStore));

            if(readController==null)
                readController = new SITransactionReadController<
                        Get,Scan,Delete,Put>(ds,dataLib, TransactionStorage.getTxnSupplier(),tc);
            Transactor transactor = new SITransactor.Builder()
                    .dataLib(dataLib)
                    .dataWriter(writer)
                    .dataStore(ds)
                    .clock(new SystemClock())
                    .transactionTimeout(transactionTimeout)
                    .txnStore(TransactionStorage.getTxnSupplier())
                    .control(transactionManager).build();
            builderTransactor.setTransactor(transactor);
            if(managedTransactor==null)
                managedTransactor = builderTransactor;

            initialized = true;
        }
    }

    public static void setTransactionManager(TransactionManager transactionManager) {
        HTransactorFactory.transactionManager = transactionManager;
    }

//		public static TimestampSource getTimestampSource() {
//				TimestampSource ts = timestampSource;
//				if(ts!=null) return ts;
//				initializeIfNeeded();
//				return timestampSource;
//		}

//		public static void setTimestampSource(TimestampSource timestampSource) {
//				HTransactorFactory.timestampSource = timestampSource;
//		}
//
//		public static void setTxnStore(TxnStore store){
//				HTransactorFactory.txnStore = store;
////				HTransactorFactory.txnSupplier = store;
//				HTransactorFactory.txnSupplier = new CompletedTxnCacheSupplier(store,SIConstants.activeTransactionCacheSize,16);
//		}

}
