package cardGame;

public class cards {
    private String name;
    private String special;
    private int atk;
    private int hp;
    private String cardID;
    private String imageURL;
    private int rarity;

    public cards(String name, String special, int atk, int hp, String cardID, String imageURL, int rarity) {
        this.name = name;
        this.special = special;
        this.atk = atk;
        this.hp = hp;
        this.cardID = cardID;
        this.imageURL = imageURL;
        this.rarity = rarity;
        
    }

    public String getName() { return name; }
    public String getSpecial() { return special; }
    public int getAtk() { return atk; }
    public int getHp() { return hp; }
    public String getCardID() { return cardID; }
    public String getImageURL() { return imageURL; }
    public int getRarity() { return rarity; }
}
