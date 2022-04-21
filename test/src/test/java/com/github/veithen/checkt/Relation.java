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

import com.github.veithen.checkt.annotation.TypeToken;

public class Relation<T1, T2> {
    private final Class<T1> type1;
    private final Class<T2> type2;

    public Relation(Class<T1> type1, Class<T2> type2) {
        this.type1 = type1;
        this.type2 = type2;
    }

    @TypeToken
    public Class<T1> getType1() {
        return type1;
    }

    @TypeToken
    public Class<T2> getType2() {
        return type2;
    }
}
