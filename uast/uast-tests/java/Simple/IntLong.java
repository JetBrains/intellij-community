/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
public class IntLong {
    public static long foo() {
        int one = 1;
        int two = one + one;
        int four = two * two;
        int sixteen = four * four;
        int twoPowerEight = sixteen * sixteen;
        int twoPowerSixteen = twoPowerEight * twoPowerEight;
        int twoPowerTwentyFour = twoPowerSixteen * twoPowerEight;
        int twoPowerThirtyTwo = twoPowerSixteen * twoPowerSixteen;

        long twoPowerFourty = ((long) twoPowerSixteen) * ((long) twoPowerTwentyFour);
        long eight = 8L;
        long twoPowerFourtyThree = twoPowerFourty * eight;
        long twoPowerFourtyEight = twoPowerFourty * twoPowerEight;
        long twoPowerFiftySix = twoPowerEight * twoPowerFourty;
        return twoPowerFiftySix;
    }
}