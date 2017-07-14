/*
 * Copyright (c) 2012 - 2017 Splice Machine, Inc.
 *
 * This file is part of Splice Machine.
 * Splice Machine is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Affero General Public License as published by the Free Software Foundation, either
 * version 3, or (at your option) any later version.
 * Splice Machine is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public License along with Splice Machine.
 * If not, see <http://www.gnu.org/licenses/>.
 */

package com.splicemachine.mrio.api.core;

import com.google.protobuf.ByteString;
import com.google.protobuf.ZeroCopyLiteralByteString;
import com.splicemachine.access.HConfiguration;
import com.splicemachine.access.hbase.HBaseTableInfoFactory;
import com.splicemachine.coprocessor.SpliceMessage;
import com.splicemachine.hbase.CellUtils;
import com.splicemachine.si.impl.HMissedSplitException;
import com.splicemachine.storage.Partition;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.coprocessor.Batch;
import org.apache.hadoop.hbase.ipc.BlockingRpcCallback;
import org.apache.hadoop.hbase.ipc.ServerRpcController;
import org.apache.hadoop.hbase.mapreduce.TableSplit;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author Scott Fines
 *         Date: 1/6/16
 */
public class HBaseSubregionSplitter implements SubregionSplitter{
    private static final Logger LOG = Logger.getLogger(HBaseSubregionSplitter.class);
    @Override
    public List<InputSplit> getSubSplits(Table table, List<Partition> splits, final byte[] scanStartRow, final byte[] scanStopRow) throws HMissedSplitException {
        List<InputSplit> results = new ArrayList<>();
        try {
        Map<byte[], List<InputSplit>> splitResults = table.coprocessorService(SpliceMessage.SpliceDerbyCoprocessorService.class, scanStartRow, scanStopRow,
                new Batch.Call<SpliceMessage.SpliceDerbyCoprocessorService, List<InputSplit>>() {
                    @Override
                    public List<InputSplit> call(SpliceMessage.SpliceDerbyCoprocessorService instance) throws IOException {
                        ServerRpcController controller = new ServerRpcController();
                        if (LOG.isDebugEnabled()) {
                            LOG.debug(String.format("Original scan [%s,%s]",
                                    CellUtils.toHex(scanStartRow), CellUtils.toHex(scanStopRow)));

                        }

                        SpliceMessage.SpliceSplitServiceRequest message = SpliceMessage.SpliceSplitServiceRequest.newBuilder()
                                .setBeginKey(ZeroCopyLiteralByteString.wrap(scanStartRow))
                                .setEndKey(ZeroCopyLiteralByteString.wrap(scanStopRow)).build();

                        BlockingRpcCallback<SpliceMessage.SpliceSplitServiceResponse> rpcCallback = new BlockingRpcCallback<>();
                        instance.computeSplits(controller, message, rpcCallback);
                        SpliceMessage.SpliceSplitServiceResponse response = rpcCallback.get();
                        if (controller.failed()) {
                            throw controller.getFailedOn();
                        }
                        List<InputSplit> result = new ArrayList<>();
                        Iterator<ByteString> it = response.getCutPointList().iterator();
                        byte[] first = it.next().toByteArray();
                        while (it.hasNext()) {
                            byte[] end = it.next().toByteArray();
                            if (Arrays.equals(first, end) && first != null && first.length > 0) {
                                // We have two cutpoints that are the same, that's only OK when the whole table fits into
                                // a single partition: ([], [])
                                LOG.warn(String.format("Two cutpoints are equal: %s", Bytes.toStringBinary(first)));
                                continue; // skip this cutpoint
                            }
                            result.add(new SMSplit(
                                    new TableSplit(
                                            table.getName(),
                                            first,
                                            end,
                                            response.getHostName())));

                            if (LOG.isDebugEnabled()) {
                                LOG.debug(String.format("New split [%s,%s]", CellUtils.toHex(first), CellUtils.toHex(end)));
                            }
                            first = end;
                        }
                        return result;
                    }
                });
        for (List<InputSplit> value : splitResults.values()) {
            results.addAll(value);
        }
    } catch (Throwable throwable) {
                LOG.error("Error while computing cutpoints, falling back to whole region partition", throwable);

                try {
                    final HBaseTableInfoFactory infoFactory = HBaseTableInfoFactory.getInstance(HConfiguration.getConfiguration());
                    for (final Partition split : splits) {
                        results.add(new SMSplit(
                                new TableSplit(
                                        infoFactory.getTableInfo(split.getTableName()),
                                        split.getStartKey(),
                                        split.getEndKey(),
                                        split.owningServer().getHostname())));
                    }
                } catch (IOException e) {
                    // Failed to add split, bail out
                    throw new RuntimeException(e);
                }
            }
        return results;
    }
}
