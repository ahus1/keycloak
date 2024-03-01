#!/bin/bash -e
#
# Copyright 2024 Red Hat, Inc. and/or its affiliates
# and other contributors as indicated by the @author tags.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

if [[ "$RUNNER_DEBUG" == "1" ]]; then
  set -x
fi

PR="$1"
REPO="$2"

if [ "$REPO" == "" ]; then
    REPO="keycloak/keycloak"
fi

if [ "$GITHUB_OUTPUT" == "" ]; then
  GITHUB_OUTPUT=/dev/stdout
fi

ISSUES=$(.github/scripts/pr-find-issues.sh "$PR" "$REPO")

for ISSUE in ${ISSUES}; do
  gh api /repos/${REPO}/issues/${ISSUE}/labels -q '.[] | select( .name | startswith("backport") ) | .name | . |= split(".") | join("-") | . |= split("/") | join("-") | . + "=true" ' \
     >> $GITHUB_OUTPUT
done
