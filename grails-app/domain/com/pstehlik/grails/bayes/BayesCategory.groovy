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

/**
 * Bayes category which is used to categorize single identifiers.
 * A <code>BayesCategory</code> can be further grouped by the <code>group</code> property.
 *
 * <p>With this additional grouping it is possible to use different stemming or classification algorithms for different
 * groups
 *
 * @author Philip Stehlik
 * @since
 */
class BayesCategory {
  Long counts
  String name
  String categoryGroup

  static constraints = {
    name(blank:false, maxSize:45, unique:true)
    categoryGroup(maxSize:32)
  }

  static mapping = {
    cache true
  }
}
