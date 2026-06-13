## 1. Wymagania wstępne

| Komponent | Wymaganie |
|-----------|-----------|
| Java JDK | 8 lub nowszy |
| SSH | dostęp do węzłów klastra labowego|
| PCJ | biblioteka PCJ (do pobrania) |

## 2. Konfiguracja SSH (dostęp do węzłów)

```bash
# Synchronizacja z klastrem labowym
source /opt/nfs/config/source_mpich32.sh
/opt/nfs/config/station204_name_list.sh 1 16 > nodes.txt
cat nodes.txt

# # Generuj klucz SSH (wymagane dla PCJ)
# ssh-keygen -t rsa -b 4096 -N ""

# # Skopiuj klucz na wszystkie węzły
# for node in stud204-{01..16}; do
#     ssh-copy-id $node
# done

# Test połączenia
ssh stud204-02 "hostname"
```

## 3. Lokalna instalacja Java JDK

```bash
cd ~
wget https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.14%2B7/OpenJDK17U-jdk_x64_linux_hotspot_17.0.14_7.tar.gz
tar -xzf OpenJDK17U-jdk_x64_linux_hotspot_17.0.14_7.tar.gz

# Ustaw zmienne
export JAVA_HOME=~/jdk-17.0.14+7
export PATH=$JAVA_HOME/bin:$PATH

# Dodaj do ~/.bashrc (trwałe)
echo 'export JAVA_HOME=~/jdk-17.0.14+7' >> ~/.bashrc
echo 'export PATH=$JAVA_HOME/bin:$PATH' >> ~/.bashrc
```

## 4. Pobranie PCJ

```bash
mkdir -p ~/rooms_problem_sa_pgas
cd ~/rooms_problem_sa_pgas
wget -P lib/ https://github.com/hpdcj/PCJ/releases/download/v5.3.4/pcj-5.3.4.jar
```
