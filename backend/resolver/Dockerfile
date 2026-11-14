FROM node:22-alpine
ENV NODE_ENV=production
WORKDIR /app
COPY package.json ./
RUN npm install --omit=dev --no-package-lock --ignore-scripts --no-audit --no-fund && npm cache clean --force
COPY src ./src
EXPOSE 3000
USER node
CMD ["node", "src/server.js"]
