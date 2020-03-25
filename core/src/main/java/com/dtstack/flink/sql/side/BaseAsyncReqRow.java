/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */



package com.dtstack.flink.sql.side;

import com.dtstack.flink.sql.enums.ECacheType;
import com.dtstack.flink.sql.metric.MetricConstant;
import com.dtstack.flink.sql.side.cache.AbstractSideCache;
import com.dtstack.flink.sql.side.cache.CacheObj;
import com.dtstack.flink.sql.side.cache.LRUSideCache;
import org.apache.calcite.sql.JoinType;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.metrics.Counter;
import org.apache.flink.streaming.api.functions.async.ResultFuture;
import org.apache.flink.streaming.api.functions.async.RichAsyncFunction;
import org.apache.flink.table.typeutils.TimeIndicatorTypeInfo;
import org.apache.flink.types.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;

/**
 * All interfaces inherit naming rules: type + "AsyncReqRow" such as == "MysqlAsyncReqRow
 * only support Left join / inner join(join),not support right join
 * Date: 2018/7/9
 * Company: www.dtstack.com
 * @author xuchao
 */

public abstract class BaseAsyncReqRow extends RichAsyncFunction<Tuple2<Boolean,Row>, Tuple2<Boolean,Row>> implements ISideReqRow {
    private static final Logger LOG = LoggerFactory.getLogger(BaseAsyncReqRow.class);
    private static final long serialVersionUID = 2098635244857937717L;

    private static int TIMEOUT_LOG_FLUSH_NUM = 10;
    private int timeOutNum = 0;

    protected BaseSideInfo sideInfo;
    protected transient Counter parseErrorRecords;

    public BaseAsyncReqRow(BaseSideInfo sideInfo){
        this.sideInfo = sideInfo;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        initCache();
        initMetric();
    }

    private void initCache(){
        AbstractSideTableInfo sideTableInfo = sideInfo.getSideTableInfo();
        if(sideTableInfo.getCacheType() == null || ECacheType.NONE.name().equalsIgnoreCase(sideTableInfo.getCacheType())){
            return;
        }

        AbstractSideCache sideCache;
        if(ECacheType.LRU.name().equalsIgnoreCase(sideTableInfo.getCacheType())){
            sideCache = new LRUSideCache(sideTableInfo);
            sideInfo.setSideCache(sideCache);
        }else{
            throw new RuntimeException("not support side cache with type:" + sideTableInfo.getCacheType());
        }
        sideCache.initCache();
    }

    private void initMetric() {
        parseErrorRecords = getRuntimeContext().getMetricGroup().counter(MetricConstant.DT_NUM_SIDE_PARSE_ERROR_RECORDS);
    }


    protected Object convertTimeIndictorTypeInfo(Integer index, Object obj) {
        boolean isTimeIndicatorTypeInfo = TimeIndicatorTypeInfo.class.isAssignableFrom(sideInfo.getRowTypeInfo().getTypeAt(index).getClass());

        //Type information for indicating event or processing time. However, it behaves like a regular SQL timestamp but is serialized as Long.
        if (obj instanceof LocalDateTime && isTimeIndicatorTypeInfo) {
            obj = Timestamp.valueOf(((LocalDateTime) obj));
        }
        return obj;
    }

    protected CacheObj getFromCache(String key){
        return sideInfo.getSideCache().getFromCache(key);
    }

    protected void putCache(String key, CacheObj value){
        sideInfo.getSideCache().putCache(key, value);
    }

    protected boolean openCache(){
        return sideInfo.getSideCache() != null;
    }

    protected void dealMissKey(Tuple2<Boolean,Row> input, ResultFuture<Tuple2<Boolean,Row>> resultFuture){
        if(sideInfo.getJoinType() == JoinType.LEFT){
            //Reserved left table data
            try {
                Row row = fillData(input.f1, null);
                resultFuture.complete(Collections.singleton(new Tuple2<>(input.f0, row)));
            } catch (Exception e) {
                dealFillDataError(resultFuture, e, input);
            }
        }else{
            resultFuture.complete(null);
        }
    }

    protected void dealCacheData(String key, CacheObj missKeyObj) {
        if (openCache()) {
            putCache(key, missKeyObj);
        }
    }

    @Override
    public void timeout(Tuple2<Boolean,Row> input, ResultFuture<Tuple2<Boolean,Row>> resultFuture) throws Exception {

        if(timeOutNum % TIMEOUT_LOG_FLUSH_NUM == 0){
            LOG.info("Async function call has timed out. input:{}, timeOutNum:{}",input.toString(), timeOutNum);
        }

        timeOutNum ++;
        if(timeOutNum > sideInfo.getSideTableInfo().getAsyncTimeoutNumLimit()){
            resultFuture.completeExceptionally(new Exception("Async function call timedoutNum beyond limit."));
        } else {
            resultFuture.complete(null);
        }
    }


    protected void dealFillDataError(ResultFuture<Tuple2<Boolean,Row>> resultFuture, Exception e, Object sourceData) {
        LOG.debug("source data {} join side table error ", sourceData);
        LOG.debug("async buid row error..{}", e);
        parseErrorRecords.inc();
        resultFuture.complete(Collections.emptyList());
    }

    @Override
    public void close() throws Exception {
        super.close();
    }
}
