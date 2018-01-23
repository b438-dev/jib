/*
 * Copyright 2018 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.tools.crepecake.builder;

import com.google.cloud.tools.crepecake.cache.Cache;
import com.google.cloud.tools.crepecake.cache.CacheChecker;
import com.google.cloud.tools.crepecake.cache.CacheWriter;
import com.google.cloud.tools.crepecake.cache.CachedLayer;
import com.google.cloud.tools.crepecake.cache.CachedLayerType;
import com.google.cloud.tools.crepecake.image.LayerBuilder;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

// TODO: Add unit test.
/** Builds and caches application layers. */
class BuildAndCacheApplicationLayersStep implements Callable<List<ListenableFuture<CachedLayer>>> {

  private final SourceFilesConfiguration sourceFilesConfiguration;
  private final Cache cache;
  private final ListeningExecutorService listeningExecutorService;

  BuildAndCacheApplicationLayersStep(
      SourceFilesConfiguration sourceFilesConfiguration,
      Cache cache,
      ListeningExecutorService listeningExecutorService) {
    this.sourceFilesConfiguration = sourceFilesConfiguration;
    this.cache = cache;
    this.listeningExecutorService = listeningExecutorService;
  }

  @Override
  public List<ListenableFuture<CachedLayer>> call() {
    List<ListenableFuture<CachedLayer>> applicationLayerFutures = new ArrayList<>(3);
    applicationLayerFutures.add(
        buildAndCacheLayerAsync(
            CachedLayerType.DEPENDENCIES,
            sourceFilesConfiguration.getDependenciesFiles(),
            sourceFilesConfiguration.getDependenciesExtractionPath()));
    applicationLayerFutures.add(
        buildAndCacheLayerAsync(
            CachedLayerType.RESOURCES,
            sourceFilesConfiguration.getResourcesFiles(),
            sourceFilesConfiguration.getResourcesExtractionPath()));
    applicationLayerFutures.add(
        buildAndCacheLayerAsync(
            CachedLayerType.CLASSES,
            sourceFilesConfiguration.getClassesFiles(),
            sourceFilesConfiguration.getClassesExtractionPath()));

    return applicationLayerFutures;
  }

  private ListenableFuture<CachedLayer> buildAndCacheLayerAsync(
      CachedLayerType layerType, Set<Path> sourceFiles, Path extractionPath) {
    return listeningExecutorService.submit(
        () -> {
          // Don't build the layer if it exists already.
          CachedLayer cachedLayer =
              new CacheChecker(cache).getUpToDateLayerBySourceFiles(sourceFiles);
          if (cachedLayer != null) {
            return cachedLayer;
          }

          LayerBuilder layerBuilder = new LayerBuilder(sourceFiles, extractionPath);

          return new CacheWriter(cache).writeLayer(layerBuilder, layerType);
        });
  }
}