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

import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.typeutils.ResultTypeQueryable;
import org.apache.flink.types.Row;

/**
 * Date: 2020/3/25
 * Company: www.dtstack.com
 * @author maqi
 */
public class TupleKeySelector implements ResultTypeQueryable<Row>, KeySelector<Tuple2<Boolean, Row>, Row> {

    private int[] keyFields;
    private TypeInformation<Row> returnType;

    public TupleKeySelector(int[] keyFields, TypeInformation<Row> returnType) {
        this.keyFields = keyFields;
        this.returnType = returnType;
    }

    @Override
    public Row getKey(Tuple2<Boolean, Row> value) throws Exception {
        return Row.project(value.f1, keyFields);
    }

    @Override
    public TypeInformation<Row> getProducedType() {
        return returnType;
    }
}
