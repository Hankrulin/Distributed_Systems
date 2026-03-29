public class request {
    RecipeRequest req = new RecipeRequest(user, ings);
    String json = om.writerWithDefaultPrettyPrinter().writeValueAsString(req);
    System.out.println(json);
}
