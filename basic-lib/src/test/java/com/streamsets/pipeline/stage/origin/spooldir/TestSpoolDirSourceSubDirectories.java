/**
 * Copyright 2016 StreamSets Inc.
 *
 * Licensed under the Apache Software Foundation (ASF) under one
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
package com.streamsets.pipeline.stage.origin.spooldir;

import com.streamsets.pipeline.api.BatchMaker;
import com.streamsets.pipeline.api.StageException;
import com.streamsets.pipeline.config.Compression;
import com.streamsets.pipeline.config.DataFormat;
import com.streamsets.pipeline.config.OnParseError;
import com.streamsets.pipeline.config.PostProcessingOptions;
import com.streamsets.pipeline.lib.dirspooler.PathMatcherMode;
import com.streamsets.pipeline.sdk.SourceRunner;
import com.streamsets.pipeline.sdk.StageRunner;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.UUID;

public class TestSpoolDirSourceSubDirectories {
  /*
   * Don't use the constant defined in SpoolDirSource in order to regression test the source.
   */
  private static final String NULL_FILE_OFFSET = "NULL_FILE_ID-48496481-5dc5-46ce-9c31-3ab3e034730c::0";

  private String createTestDir() {
    File f = new File("target", UUID.randomUUID().toString());
    Assert.assertTrue(f.mkdirs());
    return f.getAbsolutePath();
  }

  public static class TSpoolDirSource extends SpoolDirSource {
    File file;
    long offset;
    int maxBatchSize;
    long offsetIncrement;
    boolean produceCalled;
    String spoolDir;

    public TSpoolDirSource(SpoolDirConfigBean conf) {
      super(conf);
      this.spoolDir = conf.spoolDir;
    }

    @Override
    public String produce(File file, String offset, int maxBatchSize, BatchMaker batchMaker) throws StageException {
      long longOffset = Long.parseLong(offset);
      produceCalled = true;
      Assert.assertEquals(this.file, file);
      Assert.assertEquals(this.offset, longOffset);
      Assert.assertEquals(this.maxBatchSize, maxBatchSize);
      Assert.assertNotNull(batchMaker);
      return String.valueOf(longOffset + offsetIncrement);
    }
  }

  private TSpoolDirSource createSource(String initialFile) {
    SpoolDirConfigBean conf = new SpoolDirConfigBean();
    conf.dataFormat = DataFormat.TEXT;
    conf.spoolDir = createTestDir();
    conf.batchSize = 10;
    conf.overrunLimit = 100;
    conf.poolingTimeoutSecs = 1;
    conf.filePattern = "file-[0-9].log";
    conf.pathMatcherMode = PathMatcherMode.GLOB;
    conf.maxSpoolFiles = 10;
    conf.initialFileToProcess = initialFile;
    conf.dataFormatConfig.compression = Compression.NONE;
    conf.dataFormatConfig.filePatternInArchive = "*";
    conf.errorArchiveDir = null;
    conf.postProcessing = PostProcessingOptions.ARCHIVE;
    conf.archiveDir = createTestDir();
    conf.retentionTimeMins = 10;
    conf.dataFormatConfig.textMaxLineLen = 10;
    conf.dataFormatConfig.onParseError = OnParseError.ERROR;
    conf.dataFormatConfig.maxStackTraceLines = 0;
    conf.processSubdirectories = true;
    conf.useLastModified = FileOrdering.TIMESTAMP;
    return new TSpoolDirSource(conf);
  }

  @Test
  public void testInitDestroy() throws Exception {
    TSpoolDirSource source = createSource("/dir1/file-0.log");
    SourceRunner runner = new SourceRunner.Builder(TSpoolDirSource.class, source).addOutputLane("lane").build();
    runner.runInit();
    try {
      Assert.assertTrue(source.getSpooler().isRunning());
      Assert.assertEquals(runner.getContext(), source.getSpooler().getContext());
    } finally {
      runner.runDestroy();
    }
  }

  @Test
  public void getOffsetMethods() throws Exception {
    TSpoolDirSource source = createSource("/dir1/file-0.log");
    Assert.assertNull(source.getFileFromSourceOffset(null));
    Assert.assertTrue(source.getFileFromSourceOffset("/dir1/x").endsWith("/dir1/x"));
    Assert.assertEquals("0", source.getOffsetFromSourceOffset(null));
    Assert.assertEquals("0", source.getOffsetFromSourceOffset("/dir1/x"));
    Assert.assertTrue(source.getFileFromSourceOffset(source.createSourceOffset("/dir1/x", "1")).endsWith("/dir1/x"));
    Assert.assertEquals("1", source.getOffsetFromSourceOffset(source.createSourceOffset("/dir1/x", "1")));
  }

  @Test
  public void testOffsetMethods() throws Exception {
    TSpoolDirSource source = createSource(null);
    Assert.assertEquals(NULL_FILE_OFFSET, source.createSourceOffset(null, "0"));
    Assert.assertTrue(source.createSourceOffset("/dir1/file1", "0").endsWith("/dir1/file1::0"));
    Assert.assertEquals("0", source.getOffsetFromSourceOffset(NULL_FILE_OFFSET));
    Assert.assertNull(source.getFileFromSourceOffset(NULL_FILE_OFFSET));
  }

  @Test
  public void testAllowLateDirectory() throws Exception {
    File f = new File("target", UUID.randomUUID().toString());

    SpoolDirConfigBean conf = new SpoolDirConfigBean();
    conf.dataFormat = DataFormat.TEXT;
    conf.spoolDir = f.getAbsolutePath();
    conf.batchSize = 10;
    conf.overrunLimit = 100;
    conf.poolingTimeoutSecs = 1;
    conf.filePattern = "file-[0-9].log";
    conf.pathMatcherMode = PathMatcherMode.GLOB;
    conf.maxSpoolFiles = 10;
    conf.initialFileToProcess = null;
    conf.dataFormatConfig.compression = Compression.NONE;
    conf.dataFormatConfig.filePatternInArchive = "*";
    conf.errorArchiveDir = null;
    conf.postProcessing = PostProcessingOptions.ARCHIVE;
    conf.archiveDir = createTestDir();
    conf.retentionTimeMins = 10;
    conf.dataFormatConfig.textMaxLineLen = 10;
    conf.dataFormatConfig.onParseError = OnParseError.ERROR;
    conf.dataFormatConfig.maxStackTraceLines = 0;

    TSpoolDirSource source = new TSpoolDirSource(conf);
    SourceRunner runner = new SourceRunner.Builder(TSpoolDirSource.class, source).addOutputLane("lane").build();
    //Late Directories not allowed, init should fail.
    conf.allowLateDirectory = false;
    try {
      runner.runInit();
      Assert.fail("Should throw an exception if the directory does not exist");
    } catch (StageException e) {
      //Expected
    }

    //Late Directories allowed, wait and should be able to detect the file and read.
    conf.allowLateDirectory = true;
    source = new TSpoolDirSource(conf);
    runner = new SourceRunner.Builder(TSpoolDirSource.class, source).addOutputLane("lane").build();
    runner.runInit();
    try {
      StageRunner.Output output = runner.runProduce(null, 10);
      Assert.assertEquals(NULL_FILE_OFFSET, output.getNewOffset());

      Assert.assertTrue(f.mkdirs());

      File file = new File(source.spoolDir, "/dir1/file-0.log").getAbsoluteFile();
      Assert.assertTrue(file.getParentFile().mkdirs());
      Files.createFile(file.toPath());

      source.file = file;
      source.offset = 1;
      source.maxBatchSize = 10;

      Thread.sleep(1000);

      output = runner.runProduce(source.createSourceOffset("/dir1/file-0.log", "1"), 10);
      Assert.assertEquals(source.createSourceOffset("/dir1/file-0.log", "1"), output.getNewOffset());

    } finally {
      runner.runDestroy();
    }
  }

  @Test
  public void testProduceNoInitialFileWithFileInSpoolDirNullOffset() throws Exception {
    TSpoolDirSource source = createSource("/dir1/file-0.log");
    SourceRunner runner = new SourceRunner.Builder(TSpoolDirSource.class, source).addOutputLane("lane").build();
    File file = new File(source.spoolDir, "/dir1/file-0.log").getAbsoluteFile();
    Assert.assertTrue(file.getParentFile().mkdirs());
    Files.createFile(file.toPath());
    runner.runInit();
    source.file = file;
    source.offset = 0;
    source.maxBatchSize = 10;
    try {
      StageRunner.Output output = runner.runProduce(null, 10);
      Assert.assertEquals(source.createSourceOffset("dir1/file-0.log", "0"), output.getNewOffset());
      Assert.assertTrue(source.produceCalled);
    } finally {
      runner.runDestroy();
    }
  }


  @Test
  public void testProduceNoInitialFileNoFileInSpoolDirNotNullOffset() throws Exception {
    TSpoolDirSource source = createSource(null);
    SourceRunner runner = new SourceRunner.Builder(TSpoolDirSource.class, source).addOutputLane("lane").build();
    runner.runInit();
    try {
      StageRunner.Output output = runner.runProduce("/dir1/file-0.log", 10);
      Assert.assertEquals(source.createSourceOffset("/dir1/file-0.log", "0"), output.getNewOffset());
      Assert.assertFalse(source.produceCalled);
    } finally {
      runner.runDestroy();
    }
  }


  @Test
  public void testProduceNoInitialFileWithFileInSpoolDirNotNullOffset() throws Exception {
    TSpoolDirSource source = createSource("/dir1/file-0.log");
    SourceRunner runner = new SourceRunner.Builder(TSpoolDirSource.class, source).addOutputLane("lane").build();
    File file = new File(source.spoolDir, "/dir1/file-0.log").getAbsoluteFile();
    Assert.assertTrue(file.getParentFile().mkdirs());
    Files.createFile(file.toPath());
    runner.runInit();
    source.file = file;
    source.offset = 0;
    source.maxBatchSize = 10;
    try {
      StageRunner.Output output = runner.runProduce("/dir1/file-0.log", 10);
      Assert.assertEquals(source.createSourceOffset("/dir1/file-0.log", "0"), output.getNewOffset());
//      Assert.assertTrue(source.produceCalled);
    } finally {
      runner.runDestroy();
    }
  }

  @Test
  public void testProduceNoInitialFileWithFileInSpoolDirNonZeroOffset() throws Exception {
    TSpoolDirSource source = createSource(null);
    SourceRunner runner = new SourceRunner.Builder(TSpoolDirSource.class, source).addOutputLane("lane").build();
    File file = new File(source.spoolDir, "/dir1/file-0.log").getAbsoluteFile();
    Assert.assertTrue(file.getParentFile().mkdirs());
    Files.createFile(file.toPath());

    runner.runInit();
    source.file = file;
    source.offset = 1;
    source.maxBatchSize = 10;
    try {
      StageRunner.Output output = runner.runProduce(source.createSourceOffset("/dir1/file-0.log", "1"), 10);
      Assert.assertEquals(source.createSourceOffset("/dir1/file-0.log", "1"), output.getNewOffset());
//      Assert.assertTrue(source.produceCalled);
    } finally {
      runner.runDestroy();
    }
  }

  @Test
  public void testNoMoreFilesEmptyBatch() throws Exception {
    TSpoolDirSource source = createSource(null);
    SourceRunner runner = new SourceRunner.Builder(TSpoolDirSource.class, source).addOutputLane("lane").build();
    File file = new File(source.spoolDir, "dir1/file-0.log").getAbsoluteFile();
    Assert.assertTrue(file.getParentFile().mkdirs());
    Files.createFile(file.toPath());
    runner.runInit();

    try {
      source.file = file;
      source.offset = 0;
      source.maxBatchSize = 10;
      source.offsetIncrement = -1;

      StageRunner.Output output = runner.runProduce(null, 1000);
      Assert.assertEquals(source.createSourceOffset("dir1/file-0.log", "-1"), output.getNewOffset());
      Assert.assertTrue(source.produceCalled);

      source.produceCalled = false;
      output = runner.runProduce(output.getNewOffset(), 1000);
      Assert.assertEquals(source.createSourceOffset("dir1/file-0.log", "-1"), output.getNewOffset());
      //Produce will not be called as this file-0.log will not be eligible for produce
      Assert.assertFalse(source.produceCalled);
    } finally {
      runner.runDestroy();
    }
  }

}
