import java.util.*;
import java.io.*;

public class RoomAssignment {
    int n;                    // liczba studentów
    int[] room;               // room[i] = pokój studenta i
    double[][] dislike;       // macierz niechęci

    public RoomAssignment(int n, double[][] dislike) {
        this.n = n;
        this.dislike = dislike;
        this.room = new int[n];
    }

    // Losowe rozwiązanie początkowe (każdy pokój ma dokładnie 2 osoby)
    public void randomInit(Random rand) {
        int[] rooms = new int[n];
        for (int i = 0; i < n; i++) rooms[i] = i / 2;  // [0,0,1,1,2,2,...]
        for (int i = n-1; i > 0; i--) {
            int j = rand.nextInt(i+1);
            int tmp = rooms[i]; rooms[i] = rooms[j]; rooms[j] = tmp;
        }
        System.arraycopy(rooms, 0, room, 0, n);
    }

    // Obliczenie funkcji kosztu (suma niechęci dla przypisanych pokoi)
    double cost() {
        double sum = 0;
        for (int i = 0; i < n; i++)
            for (int j = i+1; j < n; j++)
                if (room[i] == room[j]) sum += dislike[i][j];
        return sum;
    }

    // zamiana przydziału dwóch losowych studentów
    RoomAssignment neighbor(Random rand) {
        RoomAssignment nei = new RoomAssignment(n, dislike);
        System.arraycopy(this.room, 0, nei.room, 0, n);
        int a = rand.nextInt(n);
        int b = rand.nextInt(n);
        while (a == b) b = rand.nextInt(n);
        int tmp = nei.room[a]; nei.room[a] = nei.room[b]; nei.room[b] = tmp;
        return nei;
    }

    // W przupadku znalezienia lepszsego rozwiązania przydiału pokoi, kopiuję je do dedykowanego
    // obiektu, przechowującego aktualnie najlepsze rozwiązanie.
    void copyFrom(RoomAssignment other) {
        System.arraycopy(other.room, 0, this.room, 0, n);
    }

    // zapisanie wyniku do pliku
    void save(String filename) throws IOException {
        PrintWriter w = new PrintWriter(new FileWriter(filename));
        w.println("=== Room Assignment Solution ===");
        w.println("Total cost: " + cost());
        w.println();
        w.println("Student -> Room:");
        for (int i = 0; i < n; i++) w.println("  " + i + " -> " + room[i]);
        w.close();
    }

    // wczytanie macierzy niechęci z pliku (dane.txt)
    static double[][] load(String filename) throws IOException {
        if (filename == null || filename.trim().isEmpty()) {
            throw new FileNotFoundException("Nie podano pliku z danymi (filename jest null/pusty).");
        }
        File file = new File(filename);
        if (!file.exists()) {
            throw new FileNotFoundException("Nie znaleziono pliku danych: " + file.getAbsolutePath());
        }

        BufferedReader r = new BufferedReader(new FileReader(filename));
        int n = Integer.parseInt(r.readLine().trim());
        double[][] d = new double[n][n];
        for (int i = 0; i < n; i++) {
            String[] parts = r.readLine().trim().split("\\s+");
            for (int j = 0; j < n; j++) d[i][j] = Double.parseDouble(parts[j]);
        }
        r.close();
        return d;
    }
}