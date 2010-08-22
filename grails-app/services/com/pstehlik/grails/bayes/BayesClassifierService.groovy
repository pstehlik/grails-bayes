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

import com.enigmastation.classifier.impl.FisherClassifierImpl
import com.enigmastation.classifier.FisherClassifier
import com.enigmastation.classifier.Trainer
import com.enigmastation.classifier.ClassifierProbability
import com.enigmastation.classifier.NaiveClassifier
import com.enigmastation.extractors.WordLister
import org.springframework.beans.factory.InitializingBean

/**
 * Classifies text based on bayesian classifiers.
 * <p>The default classification happens in a single group identified through <code>DEFAULT_GROUP</code>,
 * however if groups are configured with an appropriate <code>WordLister</code> they are taken from the config.
 * <p>An example configuration is as follows:
 * <code>
 * bayes{
 *   groupsToWordListerAssignment = [
 *     'default' : 'com.enigmastation.extractors.impl.SimpleWordLister',
 *     'my_special_group' : 'com.pstehlik.grails.bayes.SimpleWordLister'
 *   ]
 *   categoryToGroupAssignment = [
 *     'my_special_category' : 'my_special_group'
 *   ]
 * }
 * </code>
 *
 * <p> This will register two groups with different <code>WordLister</code>s and one category
 * using the <code>my_special_group</code>. This means all classification and training for elements for category
 * <code>my_special_category</code> will happen with the configured <code>WordLister</code> <code>com.pstehlik.grails.bayes.SimpleWordLister</code>.
 * All other classifications and training will happen with the <code>default</code> <code>WordLister</code>.
 *
 * <p> With this it is possible to train differently structured text with different <code>WordLister</code>s for example
 * when it is foreseeable that some categories will contain special chars, that should be taken into account etc.
 *
 * <p> There is a number of existing <code>WordLister</code> implementations coming with ci-bayes.
 * Have a look in the package <code>com.enigmastation.extractors.impl</code>
 *
 * @author Philip Stehlik
 * @since 0.1
 */
public class BayesClassifierService
implements InitializingBean {
  public static final String DEFAULT_GROUP = 'default'

  public Map<String, Class> groupsToWordListerAssignment
  public final Map<String, Class> defaultGroupToWordListerAssignment
  public Map<String, String> categoryToGroupAssignment

  boolean transactional = false
  boolean isInitialized = false
  def bayesClassifierLoaderService
  def bayesClassifierListenerService
  private classifiers = [:]

  def grailsApplication

  BayesClassifierService() {
    defaultGroupToWordListerAssignment = new HashMap<String, Class>()
    defaultGroupToWordListerAssignment.put(DEFAULT_GROUP, com.enigmastation.extractors.impl.SimpleWordLister)
  }


  void afterPropertiesSet() {
    //setting assignments of groups, classifiers and word listers from config
    grailsApplication.config.bayes?.groupsToWordListerAssignment?.each { groupToWordLister ->
      if(!groupsToWordListerAssignment){
        groupsToWordListerAssignment = new HashMap<String,Class>()
      }
      Class clazz = getClass().classLoader.loadClass(groupToWordLister.value)
      groupsToWordListerAssignment.put(groupToWordLister.key, clazz)
    }
    grailsApplication.config.bayes?.categoryToGroupAssignment?.each { categoryToGroup ->
      if(!categoryToGroupAssignment){
        categoryToGroupAssignment = new HashMap<String,String>()
      }
      categoryToGroupAssignment.put(categoryToGroup.key, categoryToGroup.value)
    }

    //setting defaults if nothing came from config
    groupsToWordListerAssignment = groupsToWordListerAssignment ?: defaultGroupToWordListerAssignment
    categoryToGroupAssignment = categoryToGroupAssignment?: new HashMap<String, String>()

    init(true)
  }

  /**
   * Set up the classifier with persisted data and all links
   * Should normall only be called by afterPropertiesSet. If executed again, just returns and does nothing unless
   * <code>forceReload</code> is set to <code>true</code>
   *
   * @param forceReload
   */
  public void init(boolean forceReload = false) {
    if (!forceReload
      && isInitialized) {
      return
    }

    groupsToWordListerAssignment.each { groupWithLister ->
      String group = groupWithLister.key
      Class wordListerClass = groupWithLister.value
      if(log.infoEnabled){ log.info "Registering classifier with class [${wordListerClass.getName()}] for group [${group}]"}

      def classifier = new FisherClassifierImpl((WordLister) wordListerClass.newInstance());

      bayesClassifierLoaderService.load(classifier, group)
      classifiers[group] = classifier
      classifier.addListener(bayesClassifierListenerService)
      log.debug "Loaded fisher classifier details: ${classifier}"
    }

    isInitialized = true
  }

  /**
   * Retrieve determination of classifier for a given text. This returns the classification with the highest
   * probability
   *
   * @param text The text to classify
   * @param cat The general category that should be used for classification
   * @return Identifier of the classification
   */
  String classifyText(String text, String group) {
    return getClassifierForGroup(group).getClassification(text)
  }

  void train(String text, String classification) {
    if (!classification) {
      throw new IllegalStateException("Can only train with classifcation. But is [${classification}]")
    }
    FisherClassifier classifier = getClassifierForGroup(classification)
    if(!classifier){
      throw new IllegalStateException("Could not load classifier to train for classification [${classification}]. Check your config.")
    }
    Trainer t = (Trainer) classifier// FisherClassifier doesn't expose train()
    t.train(text, classification)
  }

  /**
   * Determines probabilities across all classification groups
   *
   * @param text The String to classify
   * @return A <code>Collection</code> of all <code>ClassifierProbability</code> objects as identified by all different classifiers across all
   * classifier groups. If no additional classifiers had been set up this is the classifier of the <code>DEFAULT_GROUP</code>
   */
  Collection determineProbabilities(String text) {
    def ret = []
    classifiers.each {
      ((FisherClassifier) (it.value)).getProbabilities(text).each { prob ->
        ret << prob
      }
    }
    return ret
  }

  /**
   * Determines probabilities for one specific classification group
   *
   * @param text The <code>String</code> to classify
   * @param group The classifier group to use for classification
   * @return A Collection of <code>ClassifierProbability</code> objects
   */
  Collection determineProbabilities(String text, String group) {
    return getClassifierForGroup(group).getProbabilities(text) as Collection
  }

  /**
   * Determines if the probability of <code>text</code> being of <code>classification</code> is high.
   * <p>The probability is high if the resulting probability from classification is equal or higher than <code>highProbabilityIs</code>
   *
   * <p>See also {@link BayesClassifierService#isProbabilityHigh(String, String, Number, NaiveClassifier) isProbabilityHigh()}
   *
   * @param text The <code>String</code> to classify
   * @param classification The classification to check for
   * @param highProbabilityIs Probability is high if higher or equal to this
   * @return <code>true</code> if probability is high, <code>false</code> otherwise
   */
  boolean isProbabilityHigh(String text, String classification, Number highProbabilityIs) {
    def classifier = getClassifierForGroup(classification)
    return isProbabilityHigh(text, classification, highProbabilityIs, classifier)
  }

  /**
   * Determines if the probability of <code>text</code> being of <code>classification</code> is high based on the given classifier.
   * <p>The probability is high if the resulting probability from classification is equal or higher than <code>highProbabilityIs</code>
   *
   * <p>See also {@link BayesClassifierService#isProbabilityHigh(String, Collection, Number) isProbabilityHigh()}
   * @param text The <code>String</code> to classify
   * @param classification The classification to check for
   * @param highProbabilityIs Probability is high if higher or equal to this
   * @param localClassifier The classifier to use for determination
   * @return <code>true</code> if probability is high, <code>false</code> otherwise
   */
  boolean isProbabilityHigh(String text, String classification, Number highProbabilityIs, NaiveClassifier localClassifier) {
    if (log.debugEnabled) {log.debug "Checking if probability of text [${text}] to be [${classification}] is high"}
    def probs = localClassifier.getProbabilities(text)
    return isProbabilityHigh(classification, probs as Collection, highProbabilityIs)
  }

  /**
   * Determines if the probability for <code>checkCategory</code> is high based on the given set of probabilities.
   * <p>The probability is high if the <code>checkCategory</code> is the highest probability in the given <code>probabilityCollection</code>,
   * all probabilities are not the same.
   *
   * @param checkCategory The category to filter out if it has a high probability
   * @param probabilityCollection The <code>Collection</code> of all <code>com.enigmastation.classifier.ClassifierProbability</code> objects
   * @param highProbabilityIs Probability is high if higher or equal to this
   * @return <code>true</code> if probability is high, <code>false</code> otherwise
   */
  boolean isProbabilityHigh(String checkCategory, Collection probabilityCollection, Number highProbabilityIs) {
    if (log.traceEnabled) {log.trace "Checking if probability of category [${checkCategory}] is high in [${probabilityCollection}]. A high probability is score >= [${highProbabilityIs.doubleValue()}]"}


    ClassifierProbability prob = probabilityCollection.find {it.category == checkCategory}
    boolean ret
    def allProbsThatScoredSame = false
    def compareProbs = [
      compare: { a, b ->
        return a.score.compareTo(b.score)
      }
    ] as Comparator

    def sortedProbabilityCollection = new TreeSet(compareProbs)
    sortedProbabilityCollection.addAll(probabilityCollection as List)
    if (sortedProbabilityCollection.size() == 1) {
      log.debug "All probabilities turned out to be equal."
      allProbsThatScoredSame = true
    }

    if (!prob
      || probabilityCollection.size() == 1
      || (probabilityCollection.size() > 1
      && allProbsThatScoredSame)) {
      ret = false
    } else if (prob.score >= highProbabilityIs.doubleValue()) {
      log.debug "Probability score [${prob.score}] higher than threshold for high probability [${highProbabilityIs}]"
      ret = true
    } else {
      def myScore = prob.score
      Collection otherScores = probabilityCollection.find {it.category != checkCategory}*.score
      def myScoreIsHigh = false
      otherScores.each {
        if (it * 2 < myScore) {
          myScoreIsHigh = true
        }
      }
      ret = myScoreIsHigh
    }
    if (ret) {
      if (log.debugEnabled) {
        log.debug "Used probabilities: [${probabilityCollection}] for determination"
      }
    }
    if (log.debugEnabled) {log.debug "Probability is high? [${ret}]"}
    return ret
  }

  private FisherClassifier getClassifierForGroup(String group) {
    return (FisherClassifier) (classifiers[group]?:classifiers[DEFAULT_GROUP])
  }

  public String getGroupForCategoryName(String categoryName){
    return categoryToGroupAssignment[categoryName]?:DEFAULT_GROUP
  }
}