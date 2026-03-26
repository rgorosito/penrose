FROM openjdk:8-jdk-slim

# Install Ant
RUN apt-get update && \
    apt-get install -y ant && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy the project files
COPY . .

# Set Java home
ENV JAVA_HOME=/usr/local/openjdk-8
ENV PATH=$JAVA_HOME/bin:$PATH

# Run tests
CMD ["sh", "-c", "ant clean build && cd core && ant test -Dtest.name.undefined=true"]