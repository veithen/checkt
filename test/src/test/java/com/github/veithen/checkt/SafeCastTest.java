/*-
 * #%L
 * Checkt
 * %%
 * Copyright (C) 2020 - 2022 Andreas Veithen
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package com.github.veithen.checkt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class SafeCastTest {
    @Test
    public void testPartiallyCastRelation() {
        Relation<?, ?> ref1 = new Relation<String, Integer>(String.class, Integer.class);
        Relation<String, ?> ref2 = SafeCast.castByType1(ref1, String.class);
        assertThat(ref2).isSameAs(ref1);
    }

    @Test
    public void testCastSomeClass() {
        SomeClass<?, Integer> ref1 = new SomeClass<String, Integer>(String.class);
        SomeClass<String, Integer> ref2 = SafeCast.cast(ref1, String.class);
        assertThat(ref2).isSameAs(ref1);
    }
}
