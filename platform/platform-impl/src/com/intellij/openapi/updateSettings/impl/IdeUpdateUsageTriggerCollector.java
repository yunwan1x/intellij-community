// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsageTriggerCollector;
import org.jetbrains.annotations.NotNull;

public class IdeUpdateUsageTriggerCollector extends ApplicationUsageTriggerCollector {
  @NotNull
  @Override
  public String getGroupId() {
    return "statistics.ide.self.update";
  }
}
