import org.pcj.*;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

// Anotacja mowiaca o tym zeby zarejrstorwac klase jako przestrzen zmiennych wspoldzielonych w pgas
// czyli potem mogę odwolywac sie do wspolnej pamieci pcj przez nazwy pola SharedVars
@RegisterStorage(RoomAssignmentPCJ.SharedVars.class)
public class RoomAssignmentPCJ implements StartPoint {
    private static final String INPUT_FILE_MARKER = ".room_assignment_input_file";

    // Zmienne współdzielone utrzymywane na thread 0.
    // W tym wariancie utrzymujemy jeden wspólny łańcuch SA:
    // - currentRooms: globalny aktualny stan
    // - localCandidate*: kandydaci generowani równolegle przez wszystkie wątki
    @Storage(RoomAssignmentPCJ.class)
    enum SharedVars {
        currentRooms,         // int[n] - globalny stan łańcucha SA
        localCandidateCosts,  // double[threads]
        localCandidateRooms   // int[threads * n], zapis jako: id * n + student
    }

    int[] currentRooms;
    double[] localCandidateCosts;
    int[] localCandidateRooms;

    private static void persistInputFilePath(String inputFile) throws IOException {
        String absoluteInputPath = new File(inputFile).getAbsolutePath();
        Path markerPath = Paths.get(System.getProperty("user.home"), INPUT_FILE_MARKER);
        Files.write(markerPath, absoluteInputPath.getBytes(StandardCharsets.UTF_8));
    }

    private static String resolveInputFilePath() throws IOException {
        String inputFile = System.getProperty("input.file");
        if (inputFile != null && !inputFile.trim().isEmpty()) {
            return inputFile.trim();
        }

        Path markerPath = Paths.get(System.getProperty("user.home"), INPUT_FILE_MARKER);
        if (Files.exists(markerPath)) {
            List<String> lines = Files.readAllLines(markerPath, StandardCharsets.UTF_8);
            if (!lines.isEmpty()) {
                String fromMarker = lines.get(0).trim();
                if (!fromMarker.isEmpty()) {
                    return fromMarker;
                }
            }
        }

        throw new FileNotFoundException(
                "Brak sciezki do pliku danych. Uruchom program z argumentem, np.: java RoomAssignmentPCJ dane.txt");
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Uzycie: java RoomAssignmentPCJ <plik_danych>");
            System.exit(1);
        }

        String inputFile = args[0];

        // Zachowaj sciezke do danych tak, aby procesy PCJ na innych wezłach
        // mogly ja odczytac (osobne JVM nie dziedzicza lokalnych System.setProperty).
        System.setProperty("input.file", inputFile);
        try {
            persistInputFilePath(inputFile);
        } catch (IOException e) {
            System.err.println("Nie mozna zapisac konfiguracji pliku danych: " + e.getMessage());
            System.exit(2);
        }

        // uruchomienie PCJ - wywolujemy obiekty klasy RoomAssignmentPCJ na wszystkich nodach z pliku nodes.txt
        try {
            PCJ.deploy(RoomAssignmentPCJ.class, new NodesDescription("nodes.txt"));
        } catch (IOException e) {
            System.err.println("Nie mozna odczytac pliku nodes.txt: " + e.getMessage());
            System.exit(3);
        }
    }

    // Nadpisanie metody main() z interfejsu StartPoint; kod tej metody jest uruchamiany na każdym wątku/nodzie PCJ po PCJ.deploy
    // W tym wariancie wszystkie wątki wspólnie realizują JEDEN łańcuch SA.
    @Override
    public void main() throws Throwable {
        int myId = PCJ.myId();
        int threads = PCJ.threadCount();

        // Wczytaj dane
        String inputFile = resolveInputFilePath();
        double[][] dislike = RoomAssignment.load(inputFile);
        int n = dislike.length;

        // Parametry
        int ITERATIONS = 20000;
        double TEMP_START = 1.0;
        double TEMP_END = 0.001;
        double cooling = Math.pow(TEMP_END / TEMP_START, 1.0 / ITERATIONS);

        Random rand = new Random(12345L + myId);
        Random masterRand = new Random(99991L);

        if (n % 2 != 0) {
            throw new IllegalArgumentException("Liczba studentow musi byc parzysta. Otrzymano n=" + n);
        }

        // Inicjalizacje struktur na thread 0 (master łańcucha SA)
        if (myId == 0) {
            currentRooms = new int[n];
            localCandidateCosts = new double[threads];
            localCandidateRooms = new int[threads * n];

            RoomAssignment initial = new RoomAssignment(n, dislike);
            initial.randomInit(masterRand);
            System.arraycopy(initial.room, 0, currentRooms, 0, n);
            PCJ.put(currentRooms, 0, SharedVars.currentRooms);
        }

        // Poczekaj, az globalny stan poczatkowy zostanie opublikowany.
        PCJ.barrier();

        double temp = TEMP_START;
        double initialCost = -1.0;
        double globalBestCost = Double.POSITIVE_INFINITY;
        int[] globalBestRooms = null;

        for (int iter = 0; iter < ITERATIONS; iter++) {
            // Każdy wątek pobiera ten sam globalny stan bieżący.
            int[] sharedCurrent = (int[]) PCJ.get(0, SharedVars.currentRooms);
            RoomAssignment base = new RoomAssignment(n, dislike);
            System.arraycopy(sharedCurrent, 0, base.room, 0, n);

            // Każdy wątek proponuje JEDNEGO kandydata ruchu dla globalnego łańcucha SA.
            RoomAssignment candidate = base.neighbor(rand);
            double candidateCost = candidate.cost();

            // Publikacja kandydatów do thread 0.
            PCJ.put(candidateCost, 0, SharedVars.localCandidateCosts, myId);
            for (int student = 0; student < n; student++) {
                PCJ.put(candidate.room[student], 0, SharedVars.localCandidateRooms, myId * n + student);
            }

            // Poczekaj, az wszyscy opublikuja kandydatów.
            PCJ.barrier();

            if (myId == 0) {
                RoomAssignment currentSolution = new RoomAssignment(n, dislike);
                System.arraycopy(currentRooms, 0, currentSolution.room, 0, n);
                double currentCost = currentSolution.cost();

                if (iter == 0) {
                    initialCost = currentCost;
                    globalBestCost = currentCost;
                    globalBestRooms = Arrays.copyOf(currentRooms, n);
                }

                // Wybierz najlepszą propozycję z równoległej puli kandydatów.
                double winnerCost = localCandidateCosts[0];
                int winnerThread = 0;
                for (int i = 1; i < threads; i++) {
                    if (localCandidateCosts[i] < winnerCost) {
                        winnerCost = localCandidateCosts[i];
                        winnerThread = i;
                    }
                }

                double delta = winnerCost - currentCost;
                boolean accept = (delta <= 0.0) || (masterRand.nextDouble() < Math.exp(-delta / temp));
                if (accept) {
                    for (int student = 0; student < n; student++) {
                        currentRooms[student] = localCandidateRooms[winnerThread * n + student];
                    }
                    PCJ.put(currentRooms, 0, SharedVars.currentRooms);
                    currentCost = winnerCost;
                }

                if (currentCost < globalBestCost) {
                    globalBestCost = currentCost;
                    globalBestRooms = Arrays.copyOf(currentRooms, n);
                }

                temp = Math.max(TEMP_END, temp * cooling);
            }

            // Wspólny punkt iteracji - zanim przejdziemy do kolejnego kroku.
            PCJ.barrier();
        }

        // Tylko thread 0 raportuje wynik globalnego łańcucha SA.
        if (myId == 0) {
            double globalImprovement = initialCost - globalBestCost;

            // Zapisz globalnie najlepszy wynik
            RoomAssignment globalSolution = new RoomAssignment(n, dislike);
            globalSolution.room = Arrays.copyOf(globalBestRooms, n);
            String outFile = inputFile.replace(".txt", "_wynik.txt");
            globalSolution.save(outFile);
            try (PrintWriter append = new PrintWriter(new FileWriter(outFile, true))) {
                append.println();
                append.println("=== SA Distributed Single-Chain ===");
                append.println("Koszt początkowy: " + initialCost);
                append.println("Najlepszy końcowy koszt: " + globalBestCost);
                append.println("Poprawa kosztu (początkowy - końcowy): " + globalImprovement);
                append.println("Liczba użytych wątków: " + threads);
                append.println("Liczba iteracji: " + ITERATIONS);
           
            }

            System.out.println("=========================================");
            System.out.println("TRYB: DISTRIBUTED SINGLE-CHAIN SA");
            System.out.println("KOSZT STARTOWY (GLOBALNY LANCUCH): " + initialCost);
            System.out.println("GLOBALNIE NAJLEPSZY KOSZT: " + globalBestCost);
            System.out.println("ZMIANA KOSZTU (START - FINAL): " + globalImprovement);
            System.out.println("WATKI/NODY WSPOLTWORZACE LANCUCH: " + threads);
            System.out.println("Wynik zapisany do: " + outFile);
            System.out.println("=========================================");
        }
    }
}