package br.inf.ideiasistemas;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.FileWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class Main {

    // Ajuste para o endpoint que você está usando (o CNPJ é concatenado no final)
    private static final String BASE_URL = "https://open.cnpja.com/office/";
    //    private static final String BASE_URL = "https://www.receitaws.com.br/v1/cnpj/";

    public static void main(String[] args) {
        String cnpj = (args.length > 0 ? args[0] : "76553007000283").replaceAll("\\D", "");
        String dir = (args.length > 1 ? args[1] : "./");

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + cnpj))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> resp = client.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (resp.statusCode() != 200) {
                System.err.println("Erro HTTP " + resp.statusCode() + ": " + resp.body());
                return;
            }

            JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
//            System.out.println(json);

            String razaoSocial = getPorCaminho(json, "company.name");
            String municipio = getPorCaminho(json, "address.municipality");
            String endereco = getPorCaminho(json, "address.street");
            String numero = getPorCaminho(json, "address.number");
            String complemento = getPorCaminho(json, "address.details");
            String bairro = getPorCaminho(json, "address.district");
            String cidade = getPorCaminho(json, "address.city");
            String uf = getPorCaminho(json, "address.state");
            String cep = getPorCaminho(json, "address.zip");
            String telefone = getTelefone(json);
            String email = getEmail(json);
            String simples = getPorCaminho(json, "company.simples.optant");
            String simei = getPorCaminho(json, "company.simei");
            String atividade = getPorCaminho(json, "mainActivity.text");
            String dataFundacao = getPorCaminho(json, "founded");
            String capital = getPorCaminho(json, "company.equity");
            List<String> socios = getSocios(json);

            try (FileWriter writer = new FileWriter(dir + "/" + "resultado_" + cnpj + ".csv", StandardCharsets.UTF_8)) {
                writer.write("razao_social;" + razaoSocial + "\n");
                writer.write("municipio;" + municipio + "\n");
                writer.write("endereco;" + endereco + "\n");
                writer.write("numero;" + numero + "\n");
                writer.write("complemento;" + complemento + "\n");
                writer.write("bairro;" + bairro + "\n");
                writer.write("cidade;" + cidade + "\n");
                writer.write("uf;" + uf + "\n");
                writer.write("cep;" + cep + "\n");
                writer.write("telefone;" + telefone + "\n");
                writer.write("e-mail;" + email + "\n");
                writer.write("atividade;" + atividade + "\n");
                writer.write("simples;" + simples + "\n");
                writer.write("simei;" + simei + "\n");
                writer.write("dataFundacao;" + dataFundacao + "\n");
                writer.write("capital;" + capital + "\n");
                for (int i = 0; i < socios.size(); i++) {
                    writer.write("socio" + (i + 1) + ";" + socios.get(i) + "\n");
                }
                writer.write("\u001A");
            }

//            System.out.println(dir + "resultado_" + cnpj + ".csv gerado.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Lê um valor primitivo seguindo um caminho separado por pontos,
     * por exemplo "company.name" ou "address.state".
     * Retorna "" se algum nível não existir ou se o valor for nulo.
     */
    private static String getPorCaminho(JsonObject raiz, String caminho) {
        JsonElement el = raiz;
        for (String parte : caminho.split("\\.")) {
            if (el == null || !el.isJsonObject()) return "";
            el = el.getAsJsonObject().get(parte);
        }
        return (el == null || el.isJsonNull() || !el.isJsonPrimitive())
                ? ""
                : semAcentos(el.getAsString());
    }

    private static String semAcentos(String texto) {
        if (texto == null) return "";
        String normalizado = Normalizer.normalize(texto, Normalizer.Form.NFD);
        return normalizado.replaceAll("\\p{M}", "");
    }

    private static String getTelefone(JsonObject raiz) {
        JsonElement el = raiz.get("phones");
        if (el == null || !el.isJsonArray()) return "";
        JsonArray phones = el.getAsJsonArray();
        if (phones.size() == 0) return "";

        JsonObject fone = phones.get(0).getAsJsonObject();
        String area = fone.has("area") && !fone.get("area").isJsonNull() ? fone.get("area").getAsString() : "";
        String numero = fone.has("number") && !fone.get("number").isJsonNull() ? fone.get("number").getAsString() : "";
        return area + numero;
    }

    private static String getEmail(JsonObject raiz) {
        JsonElement el = raiz.get("emails");
        if (el == null || !el.isJsonArray()) return "";
        JsonArray emails = el.getAsJsonArray();
        if (emails.size() == 0) return "";

        JsonObject item = emails.get(0).getAsJsonObject();
        JsonElement addr = item.get("address");
        return (addr == null || addr.isJsonNull()) ? "" : addr.getAsString();
    }

    private static List<String> getSocios(JsonObject raiz) {
        List<String> lista = new ArrayList<>();

        JsonElement company = raiz.get("company");
        if (company == null || !company.isJsonObject()) return lista;

        JsonElement members = company.getAsJsonObject().get("members");
        if (members == null || !members.isJsonArray()) return lista;

        for (JsonElement m : members.getAsJsonArray()) {
            if (!m.isJsonObject()) continue;
            JsonElement p = m.getAsJsonObject().get("person");
            if (p == null || !p.isJsonObject()) continue;

            JsonObject person = p.getAsJsonObject();
            String tipo = str(person, "type");
            String nome = semAcentos(str(person, "name"));
            String taxId = str(person, "taxId");

            lista.add(tipo + "|" + nome + "|" + taxId);
        }
        return lista;
    }
    private static String str(JsonObject obj, String campo) {
        if (obj == null) return "";
        JsonElement el = obj.get(campo);
        return (el == null || el.isJsonNull() || !el.isJsonPrimitive()) ? "" : el.getAsString();
    }
}