/*
 * Copyright 2010 Philip Stehlik - http://www.pstehlik.com .
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pstehlik.grails.bayes

import com.enigmastation.classifier.FisherClassifier
import com.enigmastation.classifier.ClassifierMap

/**
 * Classifier loader for bayes categorization to retrieve all data from DB and attach to classifier
 *
 * @author Philip Stehlik
 * @since 0.1
 */

public class BayesClassifierLoaderService {
  boolean transactional = false

  void load(FisherClassifier classifier, String group) {
    //load all categories for that usage
    def cats = BayesCategory.findAllByCategoryGroup(group)
    cats.each {BayesCategory cat ->
      classifier.getCategoryDocCount().put(cat.name, cat.counts.toInteger())

      def feats = BayesFeature.findAllByBayesCategory(cat)
      feats.each {BayesFeature feature ->
        ClassifierMap cm = classifier.getCategoryFeatureMap().getFeature(feature.value)
        cm.put(cat.name, feature.counts.toInteger())
      }
    }
  }
}