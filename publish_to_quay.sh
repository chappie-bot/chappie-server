#!/bin/bash

# Prompt the user for input
read -p "Enter the model name (ollama/openai): " MODEL_NAME
read -p "Enter the tag (e.g., latest or v1.0.0): " TAG

# Build the source
echo "Building Native app..."
mvn clean install -Dquarkus.profile=${MODEL_NAME} -Dnative

# Build the Docker image
echo "Building Docker image..."
docker build -f src/main/docker/Dockerfile.native-micro -t chappie/chappie-server-${MODEL_NAME}:${TAG} .

# Check if the build was successful
if [ $? -ne 0 ]; then
    echo "Docker build failed. Exiting."
    exit 1
fi

# Tag the Docker image for Quay.io
echo "Tagging Docker image for Quay.io..."
docker tag chappie/chappie-server-${MODEL_NAME}:${TAG} quay.io/chappie/chappie-server-${MODEL_NAME}:${TAG}

# Push the Docker image to Quay.io
echo "Pushing Docker image to Quay.io..."
docker push quay.io/chappie/chappie-server-${MODEL_NAME}:${TAG}

# Check if the push was successful
if [ $? -eq 0 ]; then
    echo "Docker image pushed successfully to quay.io/chappie/chappie-server-${MODEL_NAME}:${TAG}"
else
    echo "Failed to push Docker image to Quay.io."
    exit 1
fi
