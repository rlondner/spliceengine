package com.splicemachine.si.impl.txn;

import com.splicemachine.si.api.txn.Txn;
import com.splicemachine.si.api.txn.TxnFactory;

/**
 * Created by jleach on 12/22/16.
 */
public class SimpleTxnFactory implements TxnFactory{
    @Override
    public Txn getTxn() {
        return new SimpleTxnImpl();
    }

    @Override
    public Txn[] getTxn(int batch) {
        return new SimpleTxnImpl[batch];
    }
}
