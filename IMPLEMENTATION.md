# ReplayCam - Descoberta de Dispositivos WiFi

## Implementação Concluída ✅

### O que foi criado:

#### 1. **Tela Principal (MainActivity)**
- Busca automática de dispositivos na rede WiFi local
- Campo de busca/filtro por nome ou IP
- Lista de dispositivos descobertos em RecyclerView
- ProgressBar durante descoberta
- Botão "Tentar Novamente" se nenhum dispositivo for encontrado

#### 2. **Descoberta de Dispositivos (mDNS/NSD)**
- `DeviceDiscoveryManager.kt`: Gerencia descoberta via NSD (Network Service Discovery)
- Localiza outros celulares com ReplayCam rodando automaticamente
- Suporta WiFi local sem precisar de internet

#### 3. **Seleção de Role**
- `RoleSelectionDialog.kt`: Diálogo para escolher entre CÂMERA ou BOTÃO
- `dialog_role_selection.xml`: Layout do diálogo com dois botões

#### 4. **Conexão entre Dispositivos**
- `ConnectionManager.kt`: Gerencia conexão via Socket TCP/IP
- Envia o role escolhido automaticamente para o outro dispositivo
- O outro dispositivo recebe automaticamente o role oposto

#### 5. **Estrutura de Dados**
- `Device.kt`: Data class com nome, IP e porta do dispositivo
- `DeviceAdapter.kt`: Adaptador para RecyclerView

#### 6. **Layouts XML**
- `activity_main.xml`: Tela de busca com SearchBar + RecyclerView
- `item_device.xml`: Item da lista de dispositivos
- `dialog_role_selection.xml`: Diálogo de seleção de role
- `rounded_edit_text.xml`: Drawable para EditText arredondado

### Permissões Adicionadas
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.CHANGE_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

## Fluxo de Uso:

1. **Iniciação**: Quando o app abre, começa a buscar dispositivos automaticamente
2. **Descoberta**: A lista se popula com dispositivos encontrados na rede
3. **Filtro**: Usuário pode digitar para filtrar por nome ou IP
4. **Seleção**: Ao clicar em um dispositivo, abre diálogo de seleção
5. **Role Automático**: 
   - Dispositivo A: Escolhe "CÂMERA" 
   - Dispositivo B: Recebe automaticamente "BOTÃO"
   - E vice-versa

## Próximos Passos (Para Implementar):

1. **Tela de Câmera**: Activity para gerenciar gravação de vídeo
2. **Tela de Botão**: Activity para botões de controle remoto
3. **Servidor NSD**: Registrar este app no mDNS para que outros o descubram
4. **Feedback de Status**: Toast/Dialog informando conexão bem-sucedida
5. **Persistência**: Salvar último dispositivo conectado
6. **Streaming de Vídeo**: Implementar streaming real do vídeo

## Testes:

Para testar em dois dispositivos:
1. Instale o app em ambos os celulares na mesma rede WiFi
2. Abra o primeiro - ele iniciará a busca
3. Abra o segundo - ele também iniciará a busca
4. Clique em um dispositivo encontrado
5. Escolha seu role (Câmera ou Botão)
6. Verifique os logs com: `adb logcat | grep MainActivity`

