/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.activemq.apollo.broker.wildcard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

import org.apache.activemq.apollo.broker.Destination;
import org.apache.activemq.apollo.broker.Router;
import org.apache.activemq.protobuf.AsciiBuffer;

public class DestinationMapTest extends TestCase {
    protected DestinationMap<String> map = new DestinationMap<String>();

    protected Destination d1 = createDestination("TEST.D1");
    protected Destination d2 = createDestination("TEST.BAR.D2");
    protected Destination d3 = createDestination("TEST.BAR.D3");
    protected Destination compositeDestination1 = createDestination("TEST.D1,TEST.BAR.D2");
    protected Destination compositeDestination2 = createDestination("TEST.D1,TEST.BAR.D3");

    protected String v1 = "value1";
    protected String v2 = "value2";
    protected String v3 = "value3";
    protected String v4 = "value4";
    protected String v5 = "value5";
    protected String v6 = "value6";

    public void testQueueAndTopicWithSameName() throws Exception {
        Destination q1 = new Destination.SingleDestination(Router.QUEUE_DOMAIN, new AsciiBuffer("foo"));
        Destination t1 = new Destination.SingleDestination(Router.TOPIC_DOMAIN, new AsciiBuffer("foo"));

        map.put(q1, v1);
        map.put(t1, v2);

        assertMapValue(q1, v1);
        assertMapValue(t1, v2);
    }

    public void testCompositeDestinations() throws Exception {
        Destination d1 = createDestination("TEST.BAR.D2");
        Destination d2 = createDestination("TEST.BAR.D3");
        map.put(d1, v1);
        map.put(d2, v2);
        map.get(createDestination("TEST.BAR.D2,TEST.BAR.D3"));

    }

    public void testSimpleDestinations() throws Exception {
        map.put(d1, v1);
        map.put(d2, v2);
        map.put(d3, v3);

        assertMapValue(d1, v1);
        assertMapValue(d2, v2);
        assertMapValue(d3, v3);
    }

    public void testSimpleDestinationsWithMultipleValues() throws Exception {
        map.put(d1, v1);
        map.put(d2, v2);
        map.put(d2, v3);

        assertMapValue(d1, v1);
        assertMapValue("TEST.BAR.D2", v2, v3);
        assertMapValue(d3, null);
    }

    public void testSimpleAndCompositeDestinations() throws Exception {
        map.put(d1, v1);
        map.put(compositeDestination1, v2);
        map.put(compositeDestination2, v3);

        Set<String> set = map.get(d1);
        System.out.println(set);
        
        
        assertMapValue("TEST.D1", v1, v2, v3);
        assertMapValue(d2, v2);
        assertMapValue(d3, v3);
        assertMapValue(compositeDestination1, v1, v2, v3);
        assertMapValue(compositeDestination2, v1, v2, v3);

        map.remove(compositeDestination1, v2);
        map.remove(compositeDestination2, v3);

        assertMapValue("TEST.D1", v1);
    }

    public void testLookupOneStepWildcardDestinations() throws Exception {
        map.put(d1, v1);
        map.put(d2, v2);
        map.put(d3, v3);

        assertMapValue("TEST.D1", v1);
        assertMapValue("TEST.*", v1);
        assertMapValue("*.D1", v1);
        assertMapValue("*.*", v1);

        assertMapValue("TEST.BAR.D2", v2);
        assertMapValue("TEST.*.D2", v2);
        assertMapValue("*.BAR.D2", v2);
        assertMapValue("*.*.D2", v2);

        assertMapValue("TEST.BAR.D3", v3);
        assertMapValue("TEST.*.D3", v3);
        assertMapValue("*.BAR.D3", v3);
        assertMapValue("*.*.D3", v3);

        assertMapValue("TEST.BAR.D4", null);

        assertMapValue("TEST.BAR.*", v2, v3);
    }

    public void testLookupMultiStepWildcardDestinations() throws Exception {
        map.put(d1, v1);
        map.put(d2, v2);
        map.put(d3, v3);

        List<String> allValues = Arrays.asList(new String[] {v1, v2, v3});

        assertMapValue(">", allValues);
        assertMapValue("TEST.>", allValues);
        assertMapValue("*.>", allValues);

        assertMapValue("FOO.>", null);
    }

    public void testStoreWildcardWithOneStepPath() throws Exception {
        put("TEST.*", v1);
        put("TEST.D1", v2);
        put("TEST.BAR.*", v2);
        put("TEST.BAR.D3", v3);

        assertMapValue("FOO", null);
        assertMapValue("TEST.FOO", v1);
        assertMapValue("TEST.D1", v1, v2);

        assertMapValue("TEST.FOO.FOO", null);
        assertMapValue("TEST.BAR.FOO", v2);
        assertMapValue("TEST.BAR.D3", v2, v3);

        assertMapValue("TEST.*", v1, v2);
        assertMapValue("*.D1", v1, v2);
        assertMapValue("*.*", v1, v2);
        assertMapValue("TEST.*.*", v2, v3);
        assertMapValue("TEST.BAR.*", v2, v3);
        assertMapValue("*.*.*", v2, v3);
        assertMapValue("*.BAR.*", v2, v3);
        assertMapValue("*.BAR.D3", v2, v3);
        assertMapValue("*.*.D3", v2, v3);
    }

    public void testStoreWildcardInMiddleOfPath() throws Exception {
        put("TEST.*", v1);
        put("TEST.D1", v2);
        put("TEST.BAR.*", v2);
        put("TEST.XYZ.D3", v3);
        put("TEST.XYZ.D4", v4);
        put("TEST.BAR.D3", v5);
        put("TEST.*.D2", v6);

        assertMapValue("TEST.*.D3", v2, v3, v5);
        assertMapValue("TEST.*.D4", v2, v4);

        assertMapValue("TEST.*", v1, v2);
        assertMapValue("TEST.*.*", v2, v3, v4, v5, v6);
        assertMapValue("TEST.*.>", v1, v2, v3, v4, v5, v6);
        assertMapValue("TEST.>", v1, v2, v3, v4, v5, v6);
        assertMapValue("TEST.>.>", v1, v2, v3, v4, v5, v6);
        assertMapValue("*.*.D3", v2, v3, v5);
        assertMapValue("TEST.BAR.*", v2, v5, v6);

        assertMapValue("TEST.BAR.D2", v2, v6);
        assertMapValue("TEST.*.D2", v2, v6);
        assertMapValue("TEST.BAR.*", v2, v5, v6);
    }

    public void testDoubleWildcardDoesNotMatchLongerPattern() throws Exception {
        put("TEST.*", v1);
        put("TEST.BAR.D3", v2);

        assertMapValue("*.*.D3", v2);
    }

    public void testWildcardAtEndOfPathAndAtBeginningOfSearch() throws Exception {
        put("TEST.*", v1);

        assertMapValue("*.D1", v1);
    }

    public void testAnyPathWildcardInMap() throws Exception {
        put("TEST.FOO.>", v1);

        assertMapValue("TEST.FOO.BAR.WHANOT.A.B.C", v1);
        assertMapValue("TEST.FOO.BAR.WHANOT", v1);
        assertMapValue("TEST.FOO.BAR", v1);

        assertMapValue("TEST.*.*", v1);
        assertMapValue("TEST.BAR", null);

        assertMapValue("TEST.FOO", v1);
    }

    public void testSimpleAddRemove() throws Exception {
        put("TEST.D1", v2);

        assertEquals("Root child count", 1, map.getRootNode().getChildCount());

        assertMapValue("TEST.D1", v2);

        remove("TEST.D1", v2);

        assertEquals("Root child count", 0, map.getRootNode().getChildCount());
        assertMapValue("TEST.D1", null);
    }

    public void testStoreAndLookupAllWildcards() throws Exception {
        loadSample2();

        assertSample2();

        // lets remove everything and add it back
        remove("TEST.FOO", v1);

        assertMapValue("TEST.FOO", v2, v3, v4);
        assertMapValue("TEST.*", v2, v3, v4, v6);
        assertMapValue("*.*", v2, v3, v4, v6);

        remove("TEST.XYZ", v6);

        assertMapValue("TEST.*", v2, v3, v4);
        assertMapValue("*.*", v2, v3, v4);

        remove("TEST.*", v2);

        assertMapValue("TEST.*", v3, v4);
        assertMapValue("*.*", v3, v4);

        remove(">", v4);

        assertMapValue("TEST.*", v3);
        assertMapValue("*.*", v3);

        remove("TEST.>", v3);
        remove("TEST.FOO.BAR", v5);

        assertMapValue("FOO", null);
        assertMapValue("TEST.FOO", null);
        assertMapValue("TEST.D1", null);

        assertMapValue("TEST.FOO.FOO", null);
        assertMapValue("TEST.BAR.FOO", null);
        assertMapValue("TEST.FOO.BAR", null);
        assertMapValue("TEST.BAR.D3", null);

        assertMapValue("TEST.*", null);
        assertMapValue("*.*", null);
        assertMapValue("*.D1", null);
        assertMapValue("TEST.*.*", null);
        assertMapValue("TEST.BAR.*", null);

        loadSample2();

        assertSample2();

        remove(">", v4);
        remove("TEST.*", v2);

        assertMapValue("FOO", null);
        assertMapValue("TEST.FOO", v1, v3);
        assertMapValue("TEST.D1", v3);

        assertMapValue("TEST.FOO.FOO", v3);
        assertMapValue("TEST.BAR.FOO", v3);
        assertMapValue("TEST.FOO.BAR", v3, v5);
        assertMapValue("TEST.BAR.D3", v3);

        assertMapValue("TEST.*", v1, v3, v6);
        assertMapValue("*.*", v1, v3, v6);
        assertMapValue("*.D1", v3);
        assertMapValue("TEST.*.*", v3, v5);
        assertMapValue("TEST.BAR.*", v3);
    }

    public void testAddAndRemove() throws Exception {

        put("FOO.A", v1);
        assertMapValue("FOO.>", v1);

        put("FOO.B", v2);
        assertMapValue("FOO.>", v1, v2);

        map.removeAll(createDestination("FOO.A"));

        assertMapValue("FOO.>", v2);

    }

    protected void loadSample2() {
        put("TEST.FOO", v1);
        put("TEST.*", v2);
        put("TEST.>", v3);
        put(">", v4);
        put("TEST.FOO.BAR", v5);
        put("TEST.XYZ", v6);
    }

    protected void assertSample2() {
        assertMapValue("FOO", v4);
        assertMapValue("TEST.FOO", v1, v2, v3, v4);
        assertMapValue("TEST.D1", v2, v3, v4);

        assertMapValue("TEST.FOO.FOO", v3, v4);
        assertMapValue("TEST.BAR.FOO", v3, v4);
        assertMapValue("TEST.FOO.BAR", v3, v4, v5);
        assertMapValue("TEST.BAR.D3", v3, v4);

        assertMapValue("TEST.*", v1, v2, v3, v4, v6);
        assertMapValue("*.*", v1, v2, v3, v4, v6);
        assertMapValue("*.D1", v2, v3, v4);
        assertMapValue("TEST.*.*", v3, v4, v5);
        assertMapValue("TEST.BAR.*", v3, v4);
    }

    protected void put(String name, String value) {
        map.put(createDestination(name), value);
    }

    protected void remove(String name, String value) {
        Destination destination = createDestination(name);
        map.remove(destination, value);
    }

    protected void assertMapValue(String destinationName, Object expected) {
        Destination destination = createDestination(destinationName);
        assertMapValue(destination, expected);
    }

    protected void assertMapValue(String destinationName, Object expected1, Object expected2) {
        assertMapValue(destinationName, Arrays.asList(new Object[] {expected1, expected2}));
    }

    protected void assertMapValue(String destinationName, Object expected1, Object expected2, Object expected3) {
        assertMapValue(destinationName, Arrays.asList(new Object[] {expected1, expected2, expected3}));
    }
    
    protected void assertMapValue(Destination destination, Object expected1, Object expected2, Object expected3) {
        assertMapValue(destination, Arrays.asList(new Object[] {expected1, expected2, expected3}));
    }

    protected void assertMapValue(String destinationName, Object expected1, Object expected2, Object expected3, Object expected4) {
        assertMapValue(destinationName, Arrays.asList(new Object[] {expected1, expected2, expected3, expected4}));
    }

    protected void assertMapValue(String destinationName, Object expected1, Object expected2, Object expected3, Object expected4, Object expected5) {
        assertMapValue(destinationName, Arrays.asList(new Object[] {expected1, expected2, expected3, expected4, expected5}));
    }

    protected void assertMapValue(String destinationName, Object expected1, Object expected2, Object expected3, Object expected4, Object expected5, Object expected6) {
        assertMapValue(destinationName, Arrays.asList(new Object[] {expected1, expected2, expected3, expected4, expected5, expected6}));
    }

    @SuppressWarnings("unchecked")
    protected void assertMapValue(Destination destination, Object expected) {
        List expectedList = null;
        if (expected == null) {
            expectedList = Collections.EMPTY_LIST;
        } else if (expected instanceof List) {
            expectedList = (List)expected;
        } else {
            expectedList = new ArrayList();
            expectedList.add(expected);
        }
        Collections.sort(expectedList);
        Set actualSet = map.get(destination);
        List actual = new ArrayList(actualSet);
        Collections.sort(actual);
        assertEquals("map value for destinationName:  " + destination, expectedList, actual);
    }

    protected Destination createDestination(String name) {
    	String[] split = name.split(",");
    	if( split.length == 1 ) {
    		return new Destination.SingleDestination(Router.QUEUE_DOMAIN, new AsciiBuffer(name));
    	} else {
    		Destination.MultiDestination rc =  new Destination.MultiDestination();
    		for (int i = 0; i < split.length; i++) {
				rc.add(createDestination(split[i]));
			}
    		return rc;
    	}
    }
}