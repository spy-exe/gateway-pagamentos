/**
 * Em producao o nginx do container encaminha /api, /saude e a documentacao
 * direto para o Spring Boot. Este rewrite existe para o ambiente local: quem
 * roda "npm run dev" junto com "./mvnw spring-boot:run" acessa tudo pela
 * mesma origem em localhost:3000 e nao esbarra em CORS.
 */
const enderecoDaApi = process.env.API_URL ?? "http://localhost:8080";

/** @type {import('next').NextConfig} */
const config = {
  async rewrites() {
    return [
      { source: "/api/:caminho*", destination: `${enderecoDaApi}/api/:caminho*` },
      { source: "/saude", destination: `${enderecoDaApi}/saude` },
      { source: "/v3/api-docs/:caminho*", destination: `${enderecoDaApi}/v3/api-docs/:caminho*` },
      { source: "/v3/api-docs", destination: `${enderecoDaApi}/v3/api-docs` },
      { source: "/swagger-ui/:caminho*", destination: `${enderecoDaApi}/swagger-ui/:caminho*` }
    ];
  }
};

export default config;
