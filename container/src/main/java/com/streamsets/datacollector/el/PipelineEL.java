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
package com.streamsets.datacollector.el;

import com.google.common.annotations.VisibleForTesting;
import com.streamsets.datacollector.config.PipelineConfiguration;
import com.streamsets.pipeline.api.ElFunction;

import java.util.HashMap;
import java.util.Map;

public class PipelineEL {

  private static final String PIPELINE_EL_PREFIX = "pipeline";
  private static final String DEFAULT_VALUE = "UNDEFINED";

  public static final String SDC_PIPELINE_TITLE_VAR = "SDC_PIPELINE_TITLE";
  public static final String SDC_PIPELINE_ID_VAR = "SDC_PIPELINE_ID";
  public static final String SDC_PIPELINE_VERSION_VAR = "SDC_PIPELINE_VERSION";

  @VisibleForTesting
  static final String PIPELINE_VERSION_VAR = "dpm.pipeline.version";

  private static final ThreadLocal<Map<String, Object>> CONSTANTS_IN_SCOPE_TL = ThreadLocal.withInitial(HashMap::new);

  @ElFunction(
    prefix = PIPELINE_EL_PREFIX,
    name = "name",
    description = "Returns the name of the pipeline")
  @Deprecated
  public static String name() {
    return getVariableFromScope(SDC_PIPELINE_ID_VAR);
  }

  @ElFunction(
    prefix = PIPELINE_EL_PREFIX,
    name = "version",
    description = "Returns the version of the pipeline if applicable. Returns \"UNDEFINED\" otherwise")
  public static String version() {
    return getVariableFromScope(SDC_PIPELINE_VERSION_VAR);
  }

  @ElFunction(
    prefix = PIPELINE_EL_PREFIX,
    name = "title",
    description = "Returns the title of the pipeline if applicable. Returns \"UNDEFINED\" otherwise")
  public static String title() {
    return getVariableFromScope(SDC_PIPELINE_TITLE_VAR);
  }

  @ElFunction(
    prefix = PIPELINE_EL_PREFIX,
    name = "id",
    description = "Returns the id of the pipeline if applicable. Returns \"UNDEFINED\" otherwise")
  public static String id() {
    return getVariableFromScope(SDC_PIPELINE_ID_VAR);
  }

  private static String getVariableFromScope(String varName) {
    Map<String, Object> variablesInScope = CONSTANTS_IN_SCOPE_TL.get();
    String name = DEFAULT_VALUE;
    if (variablesInScope.containsKey(varName)) {
      name = (String) variablesInScope.get(varName);
    }
    return name;
  }

  public static void setConstantsInContext(PipelineConfiguration pipelineConfiguration) {
    String version = DEFAULT_VALUE;
    String title = DEFAULT_VALUE;
    String id = DEFAULT_VALUE;

    if (null != pipelineConfiguration.getInfo() && null != pipelineConfiguration.getInfo().getTitle()) {
      title = pipelineConfiguration.getInfo().getTitle();
    }
    if (null != pipelineConfiguration.getInfo() && null != pipelineConfiguration.getInfo().getUuid()) {
      id = pipelineConfiguration.getInfo().getUuid().toString();
    }
    if (null != pipelineConfiguration.getMetadata() &&
        pipelineConfiguration.getMetadata().containsKey(PIPELINE_VERSION_VAR)) {
      version = (String) pipelineConfiguration.getMetadata().get(PIPELINE_VERSION_VAR);
    }
    Map<String, Object> variablesInScope = CONSTANTS_IN_SCOPE_TL.get();
    variablesInScope.put(PipelineEL.SDC_PIPELINE_VERSION_VAR, version);
    variablesInScope.put(PipelineEL.SDC_PIPELINE_ID_VAR, id);
    variablesInScope.put(PipelineEL.SDC_PIPELINE_TITLE_VAR, title);
    CONSTANTS_IN_SCOPE_TL.set(variablesInScope);
  }

  public static void unsetConstantsInContext() {
    Map<String, Object>  variablesInScope = CONSTANTS_IN_SCOPE_TL.get();
    variablesInScope.remove(PipelineEL.SDC_PIPELINE_VERSION_VAR);
    variablesInScope.remove(PipelineEL.SDC_PIPELINE_ID_VAR);
    variablesInScope.remove(PipelineEL.SDC_PIPELINE_TITLE_VAR);
    CONSTANTS_IN_SCOPE_TL.set(variablesInScope);
  }
}