/*
 * Copyright (C) 2014-2016 LinkedIn Corp. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied.
 */

package gobblin.writer.partitioner;


import com.google.common.base.Optional;
import gobblin.configuration.ConfigurationKeys;
import gobblin.configuration.State;
import gobblin.util.ForkOperatorUtils;
import gobblin.writer.partitioner.TimeBasedWriterPartitioner;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * @author cssdongl@gmail.com
 * @version V1.0
 */
public class TimeBasedJsonWriterPartitioner extends TimeBasedWriterPartitioner<byte[]> {

  private static final Logger logger = LoggerFactory.getLogger(TimeBasedJsonWriterPartitioner.class);

  public static final String WRITER_PARTITION_COLUMNS = ConfigurationKeys.WRITER_PREFIX + ".partition.columns";
  public static final String WRITER_PARTITION_COLUMNS2 = ConfigurationKeys.WRITER_PREFIX + ".partition.columns2";
  private final Optional<List<String>> partitionColumns;
  private final Optional<List<String>> partitionColumns2;
  public TimeBasedJsonWriterPartitioner(State state) {
    this(state, 1, 0);
  }

  public TimeBasedJsonWriterPartitioner(State state, int numBranches, int branchId) {
    super(state, numBranches, branchId);
    this.partitionColumns = getWriterPartitionColumns(state, numBranches, branchId);
    this.partitionColumns2 = getWriterPartitionColumns2(state, numBranches, branchId);
  }

  private static Optional<List<String>> getWriterPartitionColumns(State state, int numBranches, int branchId) {
    String propName = ForkOperatorUtils.getPropertyNameForBranch(WRITER_PARTITION_COLUMNS, numBranches, branchId);
    return state.contains(propName) ? Optional.of(state.getPropAsList(propName)) : Optional.<List<String>> absent();
  }

 private static Optional<List<String>> getWriterPartitionColumns2(State state, int numBranches, int branchId) {
    String propName = ForkOperatorUtils.getPropertyNameForBranch(WRITER_PARTITION_COLUMNS2, numBranches, branchId);
    return state.contains(propName) ? Optional.of(state.getPropAsList(propName)) : Optional.<List<String>> absent();
  }

  private long getRecordTimestamp(Optional<Long> writerPartitionColumnValue) {
    return writerPartitionColumnValue.orNull() instanceof Long ? (Long) writerPartitionColumnValue.get()
        : System.currentTimeMillis();
  }

  @Override
  public long getRecordTimestamp(byte[] record) {
    return getRecordTimestamp(getWriterPartitionColumnValue(record));
  }
    
  @Override
  public String getRecordColumn(byte[] record) {
     
    for (String partitionColumn : this.partitionColumns2.get()) {
      JSONObject jsonObject;
      try {
          String result = new String(record, "utf-8");
          jsonObject = new JSONObject(result); 
          if(jsonObject.get(partitionColumn)!=null){
            return (String) jsonObject.get(partitionColumn); 
          }else{
            return "timestamp";
          } 
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    return "timestamp";
  }
  /**
   * get the timestamp field in the json record that partition the hdfs dirs.
   */
  private Optional<Long> getWriterPartitionColumnValue(byte[] record) {
    logger.info("Get the json field begin");
    if (!this.partitionColumns.isPresent()) {
      return Optional.absent();
    }

    Optional<Long> fieldValue = Optional.absent();

    for (String partitionColumn : this.partitionColumns.get()) {
      JSONObject jsonObject;
      try {
        jsonObject = new JSONObject(new String(record, "utf-8"));
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date date;
        try {
          date = format.parse((String) jsonObject.get(partitionColumn));
          long finalTime = date.getTime();
          fieldValue = Optional.of(finalTime);
        } catch (ParseException e) {
          logger.info(e.getMessage());
        }

        if (fieldValue.isPresent()) {
          return fieldValue;
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    return fieldValue;
  }
}