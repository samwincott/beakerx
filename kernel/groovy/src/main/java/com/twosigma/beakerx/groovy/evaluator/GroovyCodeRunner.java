/*
 *  Copyright 2017 TWO SIGMA OPEN SOURCE, LLC
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.twosigma.beakerx.groovy.evaluator;

import com.twosigma.beakerx.NamespaceClient;
import com.twosigma.beakerx.evaluator.Evaluator;
import com.twosigma.beakerx.evaluator.InternalVariable;
import com.twosigma.beakerx.jvm.object.SimpleEvaluationObject;
import groovy.lang.Script;
import org.codehaus.groovy.runtime.StackTraceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;

class GroovyCodeRunner implements Runnable {

  private static final Logger logger = LoggerFactory.getLogger(GroovyCodeRunner.class.getName());
  private GroovyWorkerThread groovyWorkerThread;
  protected final String theCode;
  protected final SimpleEvaluationObject theOutput;

  public GroovyCodeRunner(GroovyWorkerThread groovyWorkerThread, String code, SimpleEvaluationObject out) {
    this.groovyWorkerThread = groovyWorkerThread;
    theCode = code;
    theOutput = out;
  }

  @Override
  public void run() {
    Object result;
    ClassLoader oldld = Thread.currentThread().getContextClassLoader();
    theOutput.setOutputHandler();

    try {

      Thread.currentThread().setContextClassLoader(groovyWorkerThread.groovyClassLoader);

      Class<?> parsedClass = groovyWorkerThread.groovyClassLoader.parseClass(theCode);

      Script instance = (Script) parsedClass.newInstance();

      if (GroovyEvaluator.LOCAL_DEV) {
        groovyWorkerThread.scriptBinding.setVariable(Evaluator.BEAKER_VARIABLE_NAME, new HashMap<String, Object>());
      } else {
        groovyWorkerThread.scriptBinding.setVariable(Evaluator.BEAKER_VARIABLE_NAME, NamespaceClient.getBeaker(groovyWorkerThread.groovyEvaluator.getSessionId()));
      }

      instance.setBinding(groovyWorkerThread.scriptBinding);

      InternalVariable.setValue(theOutput);

      result = instance.run();

      if (GroovyEvaluator.LOCAL_DEV) {
        logger.info("Result: {}", result);
        logger.info("Variables: {}", groovyWorkerThread.scriptBinding.getVariables());
      }

      theOutput.finished(result);

    } catch (Throwable e) {

      if (GroovyEvaluator.LOCAL_DEV) {
        logger.warn(e.getMessage());
        e.printStackTrace();
      }

      //unwrap ITE
      if (e instanceof InvocationTargetException) {
        e = ((InvocationTargetException) e).getTargetException();
      }

      if (e instanceof InterruptedException || e instanceof InvocationTargetException || e instanceof ThreadDeath) {
        theOutput.error("... cancelled!");
      } else {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        StackTraceUtils.sanitize(e).printStackTrace(pw);
        theOutput.error(sw.toString());
      }
    }
    theOutput.clrOutputHandler();
    Thread.currentThread().setContextClassLoader(oldld);
  }
}