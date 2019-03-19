/**
 * Copyright 2017-2019 incub8 Software Labs GmbH
 * Copyright 2017-2019 protel Hotelsoftware GmbH
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
package com.github.mizool.core.validation;

import java.util.Locale;
import java.util.Set;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

import com.google.common.collect.ImmutableSet;

public class CheckCountryCode implements ConstraintValidator<CountryCode, Object>
{
    private static final Set<String> COUNTRY_CODES = ImmutableSet.copyOf(Locale.getISOCountries());

    private boolean mandatory;

    @Override
    public void initialize(CountryCode countryCode)
    {
        mandatory = countryCode.mandatory();
    }

    @Override
    public boolean isValid(Object validationObject, ConstraintValidatorContext constraintValidatorContext)
    {
        return ConstraintValidators.isValid(validationObject, mandatory, this::isValidValue);
    }

    private boolean isValidValue(Object validationObject)
    {
        boolean valid = false;
        if (validationObject instanceof String)
        {
            String validationString = (String) validationObject;
            return !validationString.isEmpty() && COUNTRY_CODES.contains(validationString);
        }
        return valid;
    }
}