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

import com.enigmastation.classifier.ClassifierListener
import com.enigmastation.classifier.FeatureIncrement
import com.enigmastation.classifier.CategoryIncrement

/**
 * Classifier listener for bayes categorization
 *
 * @author Philip Stehlik
 * @since
 */
public class BayesClassifierListenerService
implements ClassifierListener {
  boolean transactional = false
  def bayesClassifierService

  @Override
  void handleFeatureUpdate(FeatureIncrement featureIncrement) {
    if (log.traceEnabled) {log.trace "handleFeatureUpdate [${featureIncrement}]"}
    BayesFeature feat = BayesFeature.createCriteria().get {
      bayesCategory {
        eq('name', featureIncrement.category)
      }
      eq('value', featureIncrement.feature)
      cache(true)
    }
    if (!feat) {
      def catGroup = bayesClassifierService.getGroupForCategoryName(featureIncrement.category)
      BayesCategory cat = BayesCategory.findByNameAndCategoryGroup(featureIncrement.category, catGroup)
      if (!cat) {
        cat = new BayesCategory(
          name: featureIncrement.category,
          categoryGroup: catGroup,
          counts: 0
        )
        if (cat.validate()) {
          cat.save()
        } else {
          cat.errors.each {log.error it}
          throw new RuntimeException('can not save')
        }
      }
      feat = new BayesFeature(counts: 0)
      feat.value = featureIncrement.feature
      feat.bayesCategory = cat
    }
    feat.counts += featureIncrement.count
    if (feat.validate()) {
      feat.save()
    } else {
      feat.errors.each {log.error it}
      throw new RuntimeException('can not save')
    }
  }

  @Override
  void handleCategoryUpdate(CategoryIncrement categoryIncrement) {
    if (log.traceEnabled) {log.trace "handleCategoryUpdate [${categoryIncrement}]"}
    def catGroup = bayesClassifierService.getGroupForCategoryName(categoryIncrement.category)
    BayesCategory cat = BayesCategory.findByNameAndCategoryGroup(categoryIncrement.category, catGroup)
    if (!cat) {
      cat = new BayesCategory(
        name: categoryIncrement.category,
        categoryGroup: catGroup,
        counts: 0
      )
    }
    cat.counts += categoryIncrement.count
    if (cat.validate()) {
      cat.save()
    } else {
      cat.errors.each {log.error it}
      throw new RuntimeException('can not save')
    }
  }
}
