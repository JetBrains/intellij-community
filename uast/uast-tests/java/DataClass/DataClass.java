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
public class DataClass {
    public final String STRING_CONSTANT = "ABC";

    private final String firstName;
    private final String lastName;
    private final String age;

    public DataClass(String firstName, String lastName, String age) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.age = age;
    }

    @Override
    public String toString() {
        return "DataClass{" +
                "STRING_CONSTANT='" + STRING_CONSTANT + '\'' +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", age='" + age + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DataClass dataClass = (DataClass) o;

        if (STRING_CONSTANT != null ? !STRING_CONSTANT.equals(dataClass.STRING_CONSTANT) : dataClass.STRING_CONSTANT != null)
            return false;
        if (firstName != null ? !firstName.equals(dataClass.firstName) : dataClass.firstName != null) return false;
        if (lastName != null ? !lastName.equals(dataClass.lastName) : dataClass.lastName != null) return false;
        return age != null ? age.equals(dataClass.age) : dataClass.age == null;
    }

    @Override
    public int hashCode() {
        int result = STRING_CONSTANT != null ? STRING_CONSTANT.hashCode() : 0;
        result = 31 * result + (firstName != null ? firstName.hashCode() : 0);
        result = 31 * result + (lastName != null ? lastName.hashCode() : 0);
        result = 31 * result + (age != null ? age.hashCode() : 0);
        return result;
    }
}
