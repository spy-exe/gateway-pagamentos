package br.com.ricardofigueiredo.gateway.seguranca;

import br.com.ricardofigueiredo.gateway.usuario.UsuarioRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DetalhesDoUsuarioService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public DetalhesDoUsuarioService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        return usuarioRepository.findByEmail(email)
                .map(UsuarioAutenticado::new)
                .orElseThrow(() -> new UsernameNotFoundException("usuario nao encontrado"));
    }
}
