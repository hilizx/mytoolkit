package cn.genaretor;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class QueryGenerator {
    public static void main(String[] args) {

        String methodName = "xxx";
        String methodDesc = "阿萨德";
        String sql = "SELECT a.id, a.corp_name, a.corp_credit_code, a.contact, a.contact_tel, a.contact_email, " +
                "a.loan_type, a.loan_use, a.credit_enhancement_type, a.periods, a.highest_rate, a.loan_date, " +
                "a.capital_amt, a.is_listed, a.is_auth, a.other_demands, a.corp_type, a.area, a.industry, " +
                "a.del_flag, a.create_by, a.create_time, a.update_by, a.update_time FROM t_scf_base_corp_demand AS " +
                "a LEFT JOIN t_scf_base_lable_corp_relation AS b ON a.corp_name = b.corp_name " +
                "LEFT JOIN t_scf_base_lable_info AS c ON b.lable_id = c.id WHERE a.del_flag = 0 " +
                "AND b.del_flag = 0 AND c.del_flag = 0 AND a.corp_name = '' AND a.corp_credit_code = " +
                "'' AND a.corp_type = '' AND a.loan_type = '' AND a.credit_enhancement_type = '' AND c.id = ''";

        System.out.println("==========================Controller======================================");
        System.out.println(generateCtrl(methodName,methodDesc));
        System.out.println("==========================Service======================================");
        System.out.println(generateService(methodName));
        System.out.println("==========================ServiceImpl======================================");
        System.out.println(generateServiceImpl(methodName));
        System.out.println("==========================DAO======================================");
        System.out.println(generateDao(methodName,sql));


    }
    public static String generateCtrl(String methodName, String methodDesc){
        return  "@RequestMapping(value = \"/"+methodName+"\", method = RequestMethod.POST)\n" +
                "@ApiOperation(value = \""+methodDesc+"\")\n" +
                "public Result<?> "+methodName+"(@RequestBody Map<String,String> param){\n" +
                "\tMap<String, Object> corpDemandList = service.getCorpDemandList(param);\n" +
                "\treturn new ResultUtil<>().setData(corpDemandList);\n" +
                "}";
    }
    public static String generateServiceImpl(String methodName){
        return  "@Override\n" +
                "public Map<String, Object> "+methodName+"(Map<String, String> param) {\n" +
                "\treturn dao."+methodName+"(param);\n" +
                "}";
    }

    public static String generateService(String methodName){
        return  "Map<String,Object> "+methodName+"(Map<String,String> param);";
    }


    public static String generateDao(String methodName,String sql){

        if (!sql.startsWith("SELECT")) {
            throw new RuntimeException("暂时只支持SELECT语句");
        }
        //截取字段部分
        String selectText = sql.substring(7, sql.indexOf(" FROM "));
        //截取from后面部分
        String fromText = sql.substring(sql.indexOf(" FROM ") + 6, sql.indexOf(" WHERE "));
        //截取where后面的条件
        String whereText = sql.substring(sql.indexOf(" WHERE ") + 7);


        //对每个字段添加别名
        String newColumn = Arrays.stream(selectText.split(",")).map(x -> {
            String trim = x.trim();
            String[] split = trim.split("\\.");
            return trim + " " + Tools.lineToHump(split[1]);
        }).reduce((x, y) -> x + ", " + y).orElse("");


        String queryParam = Arrays.stream(whereText.split("AND")).filter(x -> x.contains("del_flag"))
                .map(String::trim).reduce((x, y) -> x + " AND " + y).orElse("");

        List<String> str2 = Arrays.stream(whereText.split("AND")).filter(x -> !x.contains("del_flag")).map(x -> {
            x = x.trim();
            String substring = x.substring(x.indexOf(".") + 1, x.indexOf(" "));
            return x.replace("''", ":" + Tools.lineToHump(substring));
        }).collect(Collectors.toList());

        String baseSql = "SELECT " + newColumn + " FROM " + fromText + " WHERE " + queryParam;
        String countSql = "SELECT COUNT(*) FROM " + fromText + " WHERE " + queryParam;

        StringBuilder code = new StringBuilder("public Map<String,Object> "+methodName+"(Map<String,String> param){\n");
        str2.stream().map(x -> x.substring(x.indexOf(":") + 1)).forEach(x -> {
            code.append("\tString ").append(x).append(" = param.get(\"").append(x).append("\");\n");
        });
        code.append("\tint pageNum = StringUtils.isBlank(param.get(\"pageNum\"))?1:Integer.parseInt(param.get(\"pageNum\"));\n");
        code.append("\tint pageSize = StringUtils.isBlank(param.get(\"pageSize\"))?10:Integer.parseInt(param.get(\"pageSize\"));\n\n");

        code.append("\tStringBuilder sql = new StringBuilder(\"").append(baseSql).append("\");\n");
        code.append("\tStringBuilder countSql = new StringBuilder(\"").append(countSql).append("\");\n\n");

        for (String s : str2) {
            String substring = s.substring(s.indexOf(":") + 1);
            code.append("\tif(StringUtils.isNotBlank(").append(substring).append(")){\n")
                    .append("\t\tsql.append(\" AND ").append(s).append(" \");\n")
                    .append("\t\tcountSql.append(\" AND ").append(s).append(" \");\n")
                    .append("\t}\n");
        }

        code.append("\tQuery query = entityManager.createNativeQuery(sql.toString());\n");
        code.append("\tQuery countQuery = entityManager.createNativeQuery(countSql.toString());\n");

        for (String s : str2) {
            String substring = s.substring(s.indexOf(":") + 1);
            code.append("\tif(StringUtils.isNotBlank(").append(substring).append(")){\n")
                    .append("\t\tquery.setParameter(\"").append(substring).append("\",").append(substring).append(");\n")
                    .append("\t\tcountQuery.setParameter(\"").append(substring).append("\",").append(substring).append(");\n")
                    .append("\t}\n");
        }

        code.append("\tquery.unwrap(NativeQueryImpl.class).setResultTransformer(Transformers.ALIAS_TO_ENTITY_MAP);\n");
        code.append("\tquery.setFirstResult((pageNum - 1) * pageSize);\n");
        code.append("\tquery.setMaxResults(pageSize);\n");
        code.append("\tMap<String, Object> rtn_map = new HashMap<>();\n");
        code.append("\trtn_map.put(\"totalElements\", countQuery.getSingleResult());\n");
        code.append("\trtn_map.put(\"content\", query.getResultList());\n");
        code.append("\treturn rtn_map;\n");
        code.append("}");

        return code.toString();
    }

}
