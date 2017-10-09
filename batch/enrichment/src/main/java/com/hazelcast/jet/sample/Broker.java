/*
 * Copyright (c) 2008-2017, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.jet.sample;

import java.io.Serializable;

public class Broker implements Serializable {

    private int id;

    public Broker(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    @Override
    public boolean equals(Object obj) {
        Broker that;
        return obj instanceof Broker
                && this.id == (that = (Broker) obj).id;
    }

    @Override
    public int hashCode() {
        int hc = 17;
        hc = 73 * hc + id;
        return hc;
    }

    @Override
    public String toString() {
        return "Broker{id=" + id + '}';
    }
}