package org.webdifftool.client.model;
import java.util.HashMap;
import java.util.Map;

public class OperationTypeMapper {

    private static final String BASE_URL = "https://example.org/history/";

    private static final Map<String, String> operationMap = new HashMap<>();

    static {
        operationMap.put("delC", "prov:delConcept");
        operationMap.put("addC", "prov:addConcept");
        operationMap.put("mapC", "prov:mapConcept");
        operationMap.put("addA", "prov:addAttribute");
        operationMap.put("idEvent", "prov:idEvent");
        operationMap.put("delA", "prov:delAttribute");
        operationMap.put("mapA", "prov:mapAttribute");
        operationMap.put("addR", "prov:addRelationship");
        operationMap.put("delR", "prov:delRelationship");
        operationMap.put("mapR", "prov:mapRelationship");
        operationMap.put("addLeaf", "prov:addLeaf");
        operationMap.put("delLeaf", "prov:delLeaf");
        operationMap.put("move", "prov:move");
        operationMap.put("addInner", "prov:addInner");
        operationMap.put("addSubGraph", "prov:addSubGraph");
        operationMap.put("delInner", "prov:delInner");
        operationMap.put("merge", "prov:merge");
        operationMap.put("split", "prov:split");
        operationMap.put("substitute", "prov:substitute");
        operationMap.put("chgAttValue", "prov:chgAttValue");
        operationMap.put("toObsolete", "prov:toObsolete");
        operationMap.put("revokeObsolete", "prov:revokeObsolete");
    }

    public static String getOperationTypeByKey(String key, String secondSha1) {
        return operationMap.entrySet().stream()
                .filter(entry -> key.endsWith(entry.getKey()))
                .map(entry -> entry.getValue() + " <" + BASE_URL + secondSha1 + "> ;\n\t")
                .findFirst()
                .orElse("Operation type was not found. Check applied rules\n\t");
    }
}
