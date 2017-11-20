#!/bin/bash

# Hightly inspired by https://gist.github.com/domenic/ec8b0fc8ab45f39403dd

# Set the environment to be choosy
set -o errexit
set -o pipefail
set -o nounset

# Configure branches
SOURCE_BRANCH='master'
SOURCE_PATH='org.concrete5.repository/target/repository'
TARGET_BRANCH='gh-pages'
TARGET_PATH='updatesite'

# Check that the source directory exists
if [ ! -d "${SOURCE_PATH}" ]; then
   echo "Source directory ${SOURCE_PATH} not found: skipping deploy."
   exit 0
fi

# Check that we are pushing to the right branch
if [ "${TRAVIS_PULL_REQUEST:=}" != 'false' -o "${TRAVIS_BRANCH:=}" != "$SOURCE_BRANCH" ]; then
   echo "Not pushing to ${SOURCE_BRANCH}: skipping deploy."
   exit 0
fi

# Check that the GitHub encrypted token is defined
if [ -z "${GUTHUB_ACCESS_TOKEN:=}" ]; then
   echo 'Missing GUTHUB_ACCESS_TOKEN environment variable.'
   # To create it:
   #  - go to https://github.com/settings/tokens/new
   #  - create a new token
   #  - sudo apt install -y build-essential ruby ruby-dev
   #  - sudo gem install travis
   #  - travis encrypt -r <owner>/<repo> GUTHUB_ACCESS_TOKEN=<TOKEN>
   #  - Add to the env setting of:
   #   secure: "encrypted string"
   exit 0
fi

# Setup the destinaton branch settings
REMOTE_ORIGIN=`git config remote.origin.url`
if [[ ! "${REMOTE_ORIGIN}" =~ github.com/(.+)/(.+)\.git$ ]]; then
   echo "Failed to analyze remote origin: ${REMOTE_ORIGIN}"
   exit 0
fi
REPO_OWNER=${BASH_REMATCH[1]}
REPO_SLUG=${BASH_REMATCH[2]}

# Determine the SHA-1 of the last commit
COMMIT_SHA1=`git rev-parse --verify HEAD`

# Determine the email of the last commit
COMMIT_AUTHOR_EMAIL=`git log -1 --pretty="%cE"`

# Clone the existing gh-pages for this repo into deploy
git clone "${REMOTE_ORIGIN}" deploy
cd deploy
git checkout "${TARGET_BRANCH}"

# Configure git environment
git config user.name 'Travis CI'
git config user.email "${COMMIT_AUTHOR_EMAIL}"
git remote add deploy "https://${GUTHUB_ACCESS_TOKEN}@github.com/${REPO_OWNER}/${REPO_SLUG}.git"

# Remove previous update site contents
rm --recursive --force "${TARGET_PATH}"

# Copy the new update site contents
cp --recursive "../${SOURCE_PATH}" "${TARGET_PATH}"

# Add all the new files to git index
git add --all "${TARGET_PATH}"

# Create the git commit
git commit --message="Update Site refreshed after commit ${COMMIT_SHA1}"

# Push
git push deploy "${TARGET_BRANCH}"

echo 'Deployed.'
